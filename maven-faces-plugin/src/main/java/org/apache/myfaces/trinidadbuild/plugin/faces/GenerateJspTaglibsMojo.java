/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.apache.myfaces.trinidadbuild.plugin.faces;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.myfaces.trinidadbuild.plugin.faces.generator.taglib.ComponentTagGenerator;
import org.apache.myfaces.trinidadbuild.plugin.faces.generator.taglib.MyFacesComponentTagGenerator;
import org.apache.myfaces.trinidadbuild.plugin.faces.generator.taglib.TagAttributeFilter;
import org.apache.myfaces.trinidadbuild.plugin.faces.generator.taglib.TrinidadComponentTagGenerator;
import org.apache.myfaces.trinidadbuild.plugin.faces.io.PrettyWriter;
import org.apache.myfaces.trinidadbuild.plugin.faces.parse.*;
import org.apache.myfaces.trinidadbuild.plugin.faces.util.*;
import org.codehaus.plexus.util.FileUtils;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.*;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * @version $Id$
 * @requiresDependencyResolution compile
 * @goal generate-jsp-taglibs
 * @phase generate-sources
 */
public class GenerateJspTaglibsMojo extends AbstractFacesMojo
{
  /**
   * Execute the Mojo.
   */
  public void execute() throws MojoExecutionException
  {
    try
    {
      processIndex(project, resourcePath);
      _generateTagHandlers();
      _generateTagLibraryDescriptors();
    }
    catch (IOException e)
    {
      throw new MojoExecutionException("Error generating components", e);
    }
  }


  // hook for custom component tag java content
  protected void writeCustomComponentTagHandlerContent(
      PrettyWriter  out,
      ComponentBean component) throws IOException
  {
  }

  // hook for custom component tag java imports
  protected void addCustomComponentTagHandlerImports(
      Set           imports,
      ComponentBean component)
  {
  }

  // hook for custom component descriptor content
  protected void writeCustomComponentTagDescriptorContent(
      XMLStreamWriter  stream,
      ComponentBean    component)throws XMLStreamException
  {
  }


  /**
   * Generates tag library descriptors for parsed component metadata.
   */
  private void _generateTagLibraryDescriptors() throws MojoExecutionException
  {
    try
    {
      // always add resources directory to project resource root
      addResourceRoot(project, generatedResourcesDirectory.getCanonicalPath());

      // taglibs map syntax requires distinct shortNames,
      // which is a Good Thing!
      for (Iterator i = taglibs.entrySet().iterator(); i.hasNext(); )
      {
        Map.Entry entry = (Map.Entry)i.next();
        String shortName = (String)entry.getKey();
        String namespaceURI = (String)entry.getValue();

        FacesConfigBean facesConfig = getFacesConfig();
        Iterator components = facesConfig.components();
        components = new FilteredIterator(components, new SkipFilter());
        components = new FilteredIterator(components, new ComponentTagLibraryFilter(namespaceURI));

        Iterator validators = facesConfig.validators();
        validators = new FilteredIterator(validators, new ValidatorTagLibraryFilter(namespaceURI));

        Iterator converters = facesConfig.converters();
        converters = new FilteredIterator(converters, new ConverterTagLibraryFilter(namespaceURI));

        String targetPath = "META-INF/" + shortName + ".tld";
        File targetFile = new File(generatedResourcesDirectory, targetPath);

        String configPath = "META-INF/" + shortName + "-base.tld";
        File configFile = new File(configSourceDirectory, configPath);

        targetFile.delete();

        boolean hasGeneratedTags = (components.hasNext() ||
                                    converters.hasNext() ||
                                    validators.hasNext());

        if (hasGeneratedTags && configFile.exists())
        {
          ByteArrayOutputStream out = new ByteArrayOutputStream();
          XMLOutputFactory outputFactory = XMLOutputFactory.newInstance();
          XMLStreamWriter stream = outputFactory.createXMLStreamWriter(out);

          _writeStartTagLibrary(stream, _XINCLUDE_JSP_TAG_LIBRARY_DTD);
          // base goes first
          stream.writeStartElement("xi", "include",
                                   XIncludeFilter.XINCLUDE_NAMESPACE);
          stream.writeNamespace("xi", XIncludeFilter.XINCLUDE_NAMESPACE);
          stream.writeAttribute("href", configFile.toURL().toExternalForm());
          stream.writeAttribute("xpointer", "/taglib/*");
          stream.writeEndElement();
          while (components.hasNext())
          {
            ComponentBean component = (ComponentBean)components.next();
            _writeTag(stream, component);
          }
          while (converters.hasNext())
          {
            ConverterBean converter = (ConverterBean)converters.next();
            _writeTag(stream, converter);
          }
          while (validators.hasNext())
          {
            ValidatorBean validator = (ValidatorBean)validators.next();
            _writeTag(stream, validator);
          }
          _writeEndTagLibrary(stream);
          stream.close();

          InputStream mergedStream = new ByteArrayInputStream(out.toByteArray());

          // expand all the xi:include elements
          SAXParserFactory saxFactory = SAXParserFactory.newInstance();
          saxFactory.setNamespaceAware(true);
          saxFactory.setValidating(false);
          SAXParser saxParser = saxFactory.newSAXParser();
          XMLReader mergedReader = saxParser.getXMLReader();
          mergedReader = new XIncludeFilter(mergedReader, configFile.toURL());
          // even with validating=false, DTD is still downloaded so that
          // any entities contained in the document can be expanded.
          // the following disables that behavior, also saving the time
          // spent to parse the DTD
          mergedReader.setEntityResolver(new EntityResolver()
            {
              public InputSource resolveEntity(
                String publicId,
                String systemId)
              {
                return new InputSource(new ByteArrayInputStream(new byte[0]));
              }
            });
          InputSource mergedInput = new InputSource(mergedStream);
          Source mergedSource = new SAXSource(mergedReader, mergedInput);

          targetFile.delete();
          targetFile.getParentFile().mkdirs();
          Result mergedResult = new StreamResult(new FileOutputStream(targetFile));

          TransformerFactory transFactory = TransformerFactory.newInstance();
          Transformer identity = transFactory.newTransformer();
          if (!_is12())
          {
            identity.setOutputProperty(OutputKeys.DOCTYPE_PUBLIC,
                                       _JSP_TAG_LIBRARY_DOCTYPE_PUBLIC);
            identity.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM,
                                       _JSP_TAG_LIBRARY_DOCTYPE_SYSTEM);
          }

          identity.transform(mergedSource, mergedResult);

          targetFile.setReadOnly();
        }
        else if (hasGeneratedTags)
        {
          targetFile.getParentFile().mkdirs();
          OutputStream out = new FileOutputStream(targetFile);
          XMLOutputFactory outputFactory = XMLOutputFactory.newInstance();
          XMLStreamWriter stream = outputFactory.createXMLStreamWriter(out);

          _writeStartTagLibrary(stream, _is12() ? "2.1" : "1.2", shortName, namespaceURI);
          while (components.hasNext())
          {
            ComponentBean component = (ComponentBean)components.next();
            _writeTag(stream, component);
          }
          while (converters.hasNext())
          {
            ConverterBean converter = (ConverterBean)converters.next();
            _writeTag(stream, converter);
          }
          while (validators.hasNext())
          {
            ValidatorBean validator = (ValidatorBean)validators.next();
            _writeTag(stream, validator);
          }
          _writeEndTagLibrary(stream);
          stream.close();
        }
        else if (configFile.exists())
        {
          // copy if newer
          if (configFile.lastModified() > targetFile.lastModified())
          {
            targetFile.delete();
            targetFile.getParentFile().mkdirs();
            FileUtils.copyFile(configFile, targetFile);
            targetFile.setReadOnly();
          }
        }
      }
    }
    catch (SAXException e)
    {
      throw new MojoExecutionException("Error generating tag library", e);
    }
    catch (ParserConfigurationException e)
    {
      throw new MojoExecutionException("Error generating tag library", e);
    }
    catch (TransformerException e)
    {
      throw new MojoExecutionException("Error generating tag library", e);
    }
    catch (XMLStreamException e)
    {
      throw new MojoExecutionException("Error generating tag library", e);
    }
    catch (IOException e)
    {
      throw new MojoExecutionException("Error generating tag libraries", e);
    }
  }

  private void _writeStartTagLibrary(
    XMLStreamWriter stream,
    String          dtd) throws XMLStreamException
  {
    stream.writeStartDocument("1.0");
    stream.writeCharacters("\n");
    if (!_is12())
      stream.writeDTD(dtd);

    stream.writeCharacters("\n");
    stream.writeStartElement("taglib");
    if (_is12())
    {
      stream.writeNamespace("", "http://java.sun.com/xml/ns/javaee");
      stream.writeNamespace("xsi", "http://www.w3.org/2001/XMLSchema-instance");
      stream.writeAttribute("xsi:schemaLocation", "http://java.sun.com/xml/ns/javaee/web-jsptaglibrary_2_1.xsd");
      stream.writeAttribute("version", "2.1");
    }

    stream.writeCharacters("\n  ");
  }

  private void _writeStartTagLibrary(
    XMLStreamWriter stream,
    String          version,
    String          shortName,
    String          namespaceURI) throws XMLStreamException
  {
    _writeStartTagLibrary(stream, _JSP_TAG_LIBRARY_DTD);
    stream.writeStartElement("tlib-version");
    String tlibVersion = project.getVersion();
    // Remove everything but dewey-decimal characters (i.e., numbers and periods)
    tlibVersion = tlibVersion.replaceAll("[^.0-9]", "");
    // Remove leading and/or trailing periods
    while (tlibVersion.startsWith("."))
    {
      tlibVersion = tlibVersion.substring(1);
    }

    while (tlibVersion.endsWith("."))
    {
      tlibVersion = tlibVersion.substring(0, tlibVersion.length() - 1);
    }

    stream.writeCharacters(tlibVersion);
    stream.writeEndElement();

    if (!_is12())
    {
      stream.writeCharacters("\n  ");
      stream.writeStartElement("jsp-version");
      stream.writeCharacters(version);
      stream.writeEndElement();
    }
    stream.writeCharacters("\n  ");
    stream.writeStartElement("short-name");
    stream.writeCharacters(shortName);
    stream.writeEndElement();
    stream.writeCharacters("\n  ");
    stream.writeStartElement("uri");
    stream.writeCharacters(namespaceURI);
    stream.writeEndElement();
  }

  private void _writeEndTagLibrary(
    XMLStreamWriter stream) throws XMLStreamException
  {

    stream.writeCharacters("\n");
    stream.writeEndElement();
    stream.writeEndDocument();
  }

  /**
   * Generates tag library descriptor for parsed component metadata.
   */
  private void _writeTag(
    XMLStreamWriter stream,
    ComponentBean   component) throws XMLStreamException
  {
    stream.writeCharacters("\n  ");
    stream.writeStartElement("tag");
    stream.writeCharacters("\n    ");

    // In JSP 2.1, description goes up top
    if (_is12() && component.getDescription() != null)
    {
      stream.writeCharacters("\n    ");
      stream.writeStartElement("description");
      stream.writeCData(component.getDescription());
      stream.writeEndElement();
    }

    stream.writeStartElement("name");
    stream.writeCharacters(component.getTagName().getLocalPart());
    stream.writeEndElement();
    stream.writeCharacters("\n    ");
    stream.writeStartElement("tag-class");
    stream.writeCharacters(component.getTagClass());
    stream.writeEndElement();

    // In JSP 2.1, body-content is not optional
    if (_is12())
    {
      stream.writeCharacters("\n    ");
      stream.writeStartElement("body-content");
      stream.writeCharacters("JSP");
      stream.writeEndElement();
    }

    GenerateJspTaglibsMojo.this.writeCustomComponentTagDescriptorContent(stream, component);

    // In JSP 2.0, description goes just before the attributes
    if (!_is12() && component.getDescription() != null)
    {
      stream.writeCharacters("\n    ");
      stream.writeStartElement("description");
      stream.writeCData(component.getDescription());
      stream.writeEndElement();
    }

    Iterator properties = component.properties(true);
    properties = new FilteredIterator(properties, new TagAttributeFilter());
    while (properties.hasNext())
    {
      PropertyBean property = (PropertyBean)properties.next();
      writeTagAttribute(stream,
                         property.getPropertyName(),
                         property.getDescription(),
                         property.getUnsupportedAgents(),
                         property);
    }

    stream.writeCharacters("\n  ");
    stream.writeEndElement();
  }

  /**
   * Generates tag library descriptor for parsed converter metadata.
   */
  private void _writeTag(
    XMLStreamWriter stream,
    ConverterBean   converter) throws XMLStreamException
  {
    stream.writeCharacters("\n  ");
    stream.writeStartElement("tag");
    stream.writeCharacters("\n    ");
    if (_is12() && converter.getDescription() != null)
    {
      stream.writeCharacters("\n    ");
      stream.writeStartElement("description");
      stream.writeCData(converter.getDescription());
      stream.writeEndElement();
    }

    stream.writeStartElement("name");
    stream.writeCharacters(converter.getTagName().getLocalPart());
    stream.writeEndElement();
    stream.writeCharacters("\n    ");
    stream.writeStartElement("tag-class");
    stream.writeCharacters(converter.getTagClass());
    stream.writeEndElement();

    // In JSP 2.1, body-content is not optional
    if (_is12())
    {
      stream.writeCharacters("\n    ");
      stream.writeStartElement("body-content");
      stream.writeCharacters("empty");
      stream.writeEndElement();
    }

    if (!_is12() && converter.getDescription() != null)
    {
      stream.writeCharacters("\n    ");
      stream.writeStartElement("description");
      stream.writeCData(converter.getDescription());
      stream.writeEndElement();
    }

    // converters need an id attribute
    writeTagAttribute(stream, "id", "the identifier for the converter", null, null);

    Iterator properties = converter.properties();
    properties = new FilteredIterator(properties, new TagAttributeFilter());
    while (properties.hasNext())
    {
      PropertyBean property = (PropertyBean)properties.next();
      writeTagAttribute(stream,
                         property.getPropertyName(),
                         property.getDescription(),
                         property.getUnsupportedAgents(),
                         property);
    }

    stream.writeCharacters("\n  ");
    stream.writeEndElement();
  }

  private void _writeTagAttributeDescription(
    XMLStreamWriter stream,
    String          description,
    String[]        unsupportedAgents) throws XMLStreamException
  {

    if (description != null ||
        unsupportedAgents.length > 0)
    {
      stream.writeCharacters("\n      ");
      stream.writeStartElement("description");

      if (unsupportedAgents != null &&
          unsupportedAgents.length > 0)
      {
        if (description == null)
          description = "";

        description += "\n\n    This attribute is not supported on the following agent types:\n";

        for (int i=0; i < unsupportedAgents.length; i++)
        {
          description += " " + unsupportedAgents[i];
          description += (i < unsupportedAgents.length - 1) ? "," : ".";
        }
      }

      stream.writeCData(description);
      stream.writeEndElement();
    }
  }

  protected void writeTagAttribute(
    XMLStreamWriter stream,
    String          propertyName,
    String          description,
    String[]        unsupportedAgents,
    PropertyBean    property) throws XMLStreamException
  {
    stream.writeCharacters("\n    ");
    stream.writeStartElement("attribute");

    // In JSP 2.1, the description goes at the beginning
    if (_is12())
      _writeTagAttributeDescription(stream, description, unsupportedAgents);

    stream.writeCharacters("\n      ");
    stream.writeStartElement("name");
    
    if (property != null)
      stream.writeCharacters(property.getJspPropertyName());
    else
      stream.writeCharacters(propertyName);

    stream.writeEndElement();

    if (!_is12())
    {
      stream.writeCharacters("\n      ");
      stream.writeStartElement("rtexprvalue");
      stream.writeCharacters("false");
      stream.writeEndElement();

      // In JSP 2.0, the tag description goes at the end
      _writeTagAttributeDescription(stream, description, unsupportedAgents);
    }
    else
    {
      if (property != null)
      {
        if (property.isRequired())
        {
          stream.writeCharacters("\n    ");
          stream.writeStartElement("required");
          stream.writeCharacters("true");
          stream.writeEndElement();
        }
        
        if (property.isMethodExpression() || property.isMethodBinding())
        {
          stream.writeCharacters("\n    ");
          stream.writeStartElement("deferred-method");
          stream.writeCharacters("\n      ");
          MethodSignatureBean sig = property.getMethodBindingSignature();
          if (sig != null)
          {
            stream.writeStartElement("method-signature");
            stream.writeCharacters(sig.getReturnType());
            stream.writeCharacters(" myMethod(");
            String[] params = sig.getParameterTypes();
            for (int i = 0; i < params.length; i++)
            {
              if (i > 0)
                stream.writeCharacters(", ");
              stream.writeCharacters(params[i]);
            }

            stream.writeCharacters(")");
            stream.writeEndElement();
          }
          stream.writeEndElement();
        }
        else if (!property.isLiteralOnly() ||
                 // "binding" is always a deferred-value
                 "binding".equals(propertyName))
        {
          stream.writeCharacters("\n      ");
          stream.writeStartElement("deferred-value");
          String propertyClass = property.getPropertyClass();
          // Writing java.lang.String is usually a bad idea - it
          // means that null gets coerced to the empty string.
          if (("java.lang.String".equals(propertyClass) && coerceStrings) ||
              _CAN_COERCE.contains(property.getPropertyClass()))
          {
            stream.writeCharacters("\n        ");
            stream.writeStartElement("type");
            // Trim out any use of generics here - since JSP coercion
            // certainly can't do anything there
            int genericIndex = propertyClass.indexOf('<');
            if (genericIndex > 0)
              propertyClass = propertyClass.substring(0, genericIndex);

            stream.writeCharacters(propertyClass);
            stream.writeEndElement();
            stream.writeCharacters("\n      ");
          }

          stream.writeEndElement();
        }
        else
        {
          stream.writeCharacters("\n      ");
          stream.writeStartElement("rtexprvalue");
          // As of JSF 1.2, "id" can be set via an rtexprvalue (but
          // *not* by a ValueExpression) - it has to be evaluated
          // in the JSP
          if ("id".equals(propertyName) && !disableIdExpressions)
            stream.writeCharacters("true");
          else
            stream.writeCharacters("false");
          stream.writeEndElement();
        }
      }
    }

    stream.writeCharacters("\n    ");
    stream.writeEndElement();
  }

  /**
   * Generates tag library descriptor for parsed validator metadata.
   */
  private void _writeTag(
    XMLStreamWriter stream,
    ValidatorBean   validator) throws XMLStreamException
  {
    stream.writeCharacters("\n  ");
    stream.writeStartElement("tag");

    if (_is12() && validator.getDescription() != null)
    {
      stream.writeCharacters("\n    ");
      stream.writeStartElement("description");
      stream.writeCData(validator.getDescription());
      stream.writeEndElement();
    }

    stream.writeCharacters("\n    ");
    stream.writeStartElement("name");
    stream.writeCharacters(validator.getTagName().getLocalPart());
    stream.writeEndElement();
    stream.writeCharacters("\n    ");
    stream.writeStartElement("tag-class");
    stream.writeCharacters(validator.getTagClass());
    stream.writeEndElement();

    // In JSP 2.1, body-content is not optional
    if (_is12())
    {
      stream.writeCharacters("\n    ");
      stream.writeStartElement("body-content");
      stream.writeCharacters("empty");
      stream.writeEndElement();
    }

    if (!_is12() && validator.getDescription() != null)
    {
      stream.writeCharacters("\n    ");
      stream.writeStartElement("description");
      stream.writeCData(validator.getDescription());
      stream.writeEndElement();
    }

    // validators need an id attribute
    writeTagAttribute(stream, "id", "the identifier for the validator", null, null);

    Iterator properties = validator.properties();
    properties = new FilteredIterator(properties, new TagAttributeFilter());
    while (properties.hasNext())
    {
      PropertyBean property = (PropertyBean)properties.next();
      writeTagAttribute(stream,
                         property.getPropertyName(),
                         property.getDescription(),
                         property.getUnsupportedAgents(),
                         property);
    }

    stream.writeCharacters("\n  ");
    stream.writeEndElement();
  }

  /**
   * Generates tag handlers for parsed component metadata.
   */
  private void _generateTagHandlers() throws IOException
  {
    // Make sure generated source directory
    // is added to compilation source path
    project.addCompileSourceRoot(generatedSourceDirectory.getCanonicalPath());

    FacesConfigBean facesConfig = getFacesConfig();
    if (!facesConfig.hasComponents())
    {
      getLog().info("Nothing to generate - no components found");
    }
    else
    {
      Iterator components = facesConfig.components();
      components = new FilteredIterator(components, new SkipFilter());
      components = new FilteredIterator(components, new ComponentTagFilter());
      components = new FilteredIterator(components, new ComponentTagClassFilter(packageContains));

      Iterator validators = facesConfig.validators();
      validators = new FilteredIterator(validators, new ValidatorTagFilter());
      validators = new FilteredIterator(validators, new ValidatorTagClassFilter(packageContains));

      Iterator converters = facesConfig.converters();
      converters = new FilteredIterator(converters, new ConverterTagFilter());
      converters = new FilteredIterator(converters, new ConverterTagClassFilter(packageContains));

      // incremental unless forced
      if (!force)
      {
        components = new FilteredIterator(components, new IfComponentModifiedFilter());
        converters = new FilteredIterator(converters, new IfConverterModifiedFilter());
        validators = new FilteredIterator(validators, new IfValidatorModifiedFilter());
      }

      if (!components.hasNext() && !converters.hasNext())
      {
        getLog().info("Nothing to generate - all JSP tags are up to date");
      }
      else
      {
        ComponentTagHandlerGenerator componentGen = new ComponentTagHandlerGenerator();
        ConverterTagGenerator converterGen = new ConverterTagGenerator();
        ValidatorTagGenerator validatorGen = new ValidatorTagGenerator();
        int count = 0;
        while (components.hasNext())
        {
          componentGen.generateTagHandler((ComponentBean)components.next());
          count++;
        }
        while (converters.hasNext())
        {
          converterGen.generateTagHandler((ConverterBean)converters.next());
          count++;
        }
        while (validators.hasNext())
        {
          validatorGen.generateTagHandler((ValidatorBean)validators.next());
          count++;
        }
        getLog().info("Generated " + count + " JSP tag(s)");
      }
    }
  }

  class ConverterTagGenerator
  {
    public void generateTagHandler(
      ConverterBean converter)
    {
      String fullClassName = converter.getTagClass();

      try
      {
        getLog().debug("Generating " + fullClassName);

        String sourcePath = Util.convertClassToSourcePath(fullClassName, ".java");
        File targetFile = new File(generatedSourceDirectory, sourcePath);

        targetFile.getParentFile().mkdirs();
        StringWriter sw = new StringWriter();
        PrettyWriter out = new PrettyWriter(sw);

        String className = Util.getClassFromFullClass(fullClassName);
        String packageName = Util.getPackageFromFullClass(fullClassName);

        // header/copyright
        writePreamble(out);

        // package
        out.println("package " + packageName + ";");

        out.println();
        _writeImports(out, converter);

        out.println("/**");
        // TODO: remove this blank line.
        out.println();
        out.println(" * Auto-generated tag class.");
        out.println(" */");

        if (_is12())
        {
          out.println("public class " + className +
                      " extends ConverterELTag");
        }
        else
        {
          out.println("public class " + className +
                      " extends ConverterTag");
        }

        out.println("{");
        out.indent();

        _writeConstructor(out, converter);

        _writePropertyMethods(out, converter);
        _writeDoStartTag(out, converter);
        _writeCreateConverter(out, converter);
        _writeSetProperties(out, converter);
        _writeRelease(out, converter);

        out.unindent();
        out.println("}");
        out.close();

        // delay write in case of error
        // timestamp should not be updated when an error occurs
        // delete target file first, because it is readonly
        targetFile.delete();
        FileWriter fw = new FileWriter(targetFile);
        StringBuffer buf = sw.getBuffer();
        fw.write(buf.toString());
        fw.close();
        targetFile.setReadOnly();
      }
      catch (Throwable e)
      {
        getLog().error("Error generating " + fullClassName, e);
      }
    }

    private void _writeImports(
      PrettyWriter   out,
      ConverterBean  converter)
    {
      Set imports = new TreeSet();

      if (_is12())
      {
        imports.add("javax.faces.webapp.ConverterELTag");
        imports.add("javax.faces.context.FacesContext");
        imports.add("javax.faces.application.Application");
      }
      else
        imports.add("javax.faces.webapp.ConverterTag");
        
      imports.add("javax.servlet.jsp.JspException");
      imports.add(converter.getConverterClass());

      Iterator properties = converter.properties();
      properties = new FilteredIterator(properties, new TagAttributeFilter());
      if (properties.hasNext())
      {
        imports.add("javax.faces.convert.Converter");
        if (_is12())
          imports.add("javax.el.ValueExpression");
        else
          imports.add("javax.faces.el.ValueBinding");
        imports.add("org.apache.myfaces.trinidadinternal.taglib.util.TagUtils");
      }

      while (properties.hasNext())
      {
        PropertyBean property = (PropertyBean)properties.next();

        String propertyClass = property.getPropertyClass();
        if (propertyClass != null)
          imports.add(propertyClass);

        if ("java.lang.String[]".equals(propertyClass))
        {
          imports.add("java.text.ParseException");
        }
      }

      // do not import implicit!
      imports.removeAll(Util.PRIMITIVE_TYPES);

      String tagClass = converter.getTagClass();
      String packageName = Util.getPackageFromFullClass(tagClass);
      writeImports(out, packageName, imports);
    }

    private void _writeConstructor(
      PrettyWriter  out,
      ConverterBean converter) throws IOException
    {
      String fullClassName = converter.getTagClass();
      String className = Util.getClassFromFullClass(fullClassName);
      out.println();
      out.println("/**");
      // TODO: restore this correctly phrased comment (tense vs. command)
      //out.println(" * Constructs an instance of " + className + ".");
      out.println(" * Construct an instance of the " + className + ".");
      out.println(" */");
      out.println("public " + className + "()");
      out.println("{");
      out.println("}");
    }

    private void _writePropertyMethods(
      PrettyWriter  out,
      ConverterBean converter) throws IOException
    {
      Iterator properties = converter.properties();
      properties = new FilteredIterator(properties, new TagAttributeFilter());
      while (properties.hasNext())
      {
        PropertyBean property = (PropertyBean)properties.next();
        out.println();
        _writePropertyMember(out, property);
        _writePropertySet(out, property);
      }
    }

    private void _writePropertyMember(
     PrettyWriter  out,
     PropertyBean  property) throws IOException
    {
      String propName = property.getPropertyName();
      String propVar = "_" + Util.getVariableFromName(propName);
      String jspPropType = _getJspPropertyType(property);

      out.println("private " + jspPropType + " " + propVar + ";");
    }

    private void _writePropertySet(
     PrettyWriter  out,
     PropertyBean  property) throws IOException
    {
      String propName = property.getPropertyName();
      String propVar = Util.getVariableFromName(propName);
      String setMethod = Util.getPrefixedPropertyName("set", propName);
      String jspPropType = _getJspPropertyType(property);

      // TODO: restore coding standards, and make final
      out.println("public void " + setMethod + "(" + jspPropType + " " + propVar + ")");
      out.println("{");
      out.indent();
      out.println("_" + propVar + " = " + propVar + ";");
      out.unindent();
      out.println("}");
    }

    private void _writeDoStartTag(
      PrettyWriter  out,
      ConverterBean converter) throws IOException
    {
      if (!_is12())
      {
        String converterFullClass = converter.getConverterClass();
        String converterClass = Util.getClassFromFullClass(converterFullClass);
        
        out.println();
        // TODO: restore coding standards, and make final
        out.println("@Override");
        out.println("public int doStartTag() throws JspException");
        out.println("{");
        out.indent();
        out.println("super.setConverterId(" + converterClass + ".CONVERTER_ID);");
        out.println("return super.doStartTag();");
        out.unindent();
        out.println("}");
      }
    }

    private void _writeCreateConverter(
      PrettyWriter  out,
      ConverterBean converter) throws IOException
    {
      Iterator properties = converter.properties();
      properties = new FilteredIterator(properties, new TagAttributeFilter());
      if (properties.hasNext())
      {
        String converterFullClass = converter.getConverterClass();
        String converterClass = Util.getClassFromFullClass(converterFullClass);

        out.println();
        // TODO: restore coding standards, and make final
        out.println("@Override");
        out.println("protected Converter createConverter() throws JspException");
        out.println("{");
        out.indent();
        if (_is12())
        {
          out.println("String converterId = " + converterClass +  ".CONVERTER_ID;");
          out.println("Application appl = FacesContext.getCurrentInstance().getApplication();");
          out.println(converterClass + " converter = " +
                      "(" + converterClass + ")appl.createConverter(converterId);");
        }
        else
        {
          out.println(converterClass + " converter = " +
                      "(" + converterClass + ")super.createConverter();");
        }
        out.println("_setProperties(converter);");
        out.println("return converter;");
        out.unindent();
        out.println("}");
      }
    }

    private void _writeSetProperties(
      PrettyWriter  out,
      ConverterBean converter) throws IOException
    {
      Iterator properties = converter.properties();
      properties = new FilteredIterator(properties, new TagAttributeFilter());
      if (properties.hasNext())
      {
        String converterFullClass = converter.getConverterClass();
        String converterClass = Util.getClassFromFullClass(converterFullClass);
        out.println();
        out.println("private void _setProperties(");
        out.indent();
        out.println(converterClass + " converter) throws JspException");
        out.unindent();
        out.println("{");
        out.indent();
        while (properties.hasNext())
        {
          PropertyBean property = (PropertyBean)properties.next();
          _writeSetProperty(out, property);
        }
        out.unindent();
        out.println("}");
      }
    }

    private void _writeSetProperty(
      PrettyWriter out,
      PropertyBean property)
    {
      String propName = property.getPropertyName();
      String propFullClass = property.getPropertyClass();
      String propClass = Util.getClassFromFullClass(propFullClass);
      String propVar = "_" + Util.getVariableFromName(propName);

      out.println("if (" + propVar + " != null)");
      out.println("{");
      out.indent();

      if (_is12())
      {
        out.println("if (!" + propVar + ".isLiteralText())");
        out.println("{");
        out.indent();
        out.println("converter.setValueExpression(\"" + propName + "\", " +
                    propVar + ");");
        out.unindent();
        out.println("}");
        String propType = _resolveType(propFullClass);
        if (propType != null)
        {
          out.println("else");
          out.println("{");
          out.indent();
          if ("StringArray".equals(propType))
          {
            out.println("try");
            out.println("{");
          }
          
          out.println(propClass + " value = TagUtils.get" + propType + "(" + propVar + ".getValue(null));");
          String setMethod = Util.getPrefixedPropertyName("set", propName);
          out.println("converter." + setMethod + "(value);");
          if ("StringArray".equals(propType))
          {
            out.println("}");
            out.println("catch (ParseException pe)");
            out.println("{");
            out.indent();
            out.println("throw new JspException(");
            out.println("  pe.getMessage() + \": \" + \"Position \" + pe.getErrorOffset());");
            out.unindent();
            out.println("}");
          }
          out.unindent();
          out.println("}");
        }
      }
      else
      {
        out.println("if (TagUtils.isValueReference(" + propVar + "))");
        out.println("{");
        out.indent();
        out.println("ValueBinding vb = TagUtils.getValueBinding(" + propVar + ");");
        out.println("converter.setValueBinding(\"" + propName + "\", vb);");
        out.unindent();
        out.println("}");
        String propType = _resolveType(propFullClass);
        if (propType != null)
        {
          out.println("else");
          out.println("{");
          out.indent();
          if ("StringArray".equals(propType))
          {
            out.println("try");
            out.println("{");
          }
          out.println(propClass + " value = TagUtils.get" + propType + "(" + propVar + ");");
          String setMethod = Util.getPrefixedPropertyName("set", propName);
          out.println("converter." + setMethod + "(value);");
          if ("StringArray".equals(propType))
          {
            out.println("}");
            out.println("catch (ParseException pe)");
            out.println("{");
            out.indent();
            out.println("throw new JspException(");
            out.println("  pe.getMessage() + \": \" + \"Position \" + pe.getErrorOffset());");
            out.unindent();
            out.println("}");
          }
          out.unindent();
          out.println("}");
        }
      }

      out.unindent();
      out.println("}");
    }

    private void _writeRelease(
      PrettyWriter  out,
      ConverterBean converter) throws IOException
    {
      Iterator properties = converter.properties();
      properties = new FilteredIterator(properties, new TagAttributeFilter());
      if (properties.hasNext())
      {
        out.println();
        out.println("@Override");
        out.println("public void release()");
        out.println("{");
        out.indent();
        out.println("super.release();");
        while (properties.hasNext())
        {
          PropertyBean property = (PropertyBean)properties.next();
          String propName = property.getPropertyName();
          String propVar = "_" + Util.getVariableFromName(propName);
          out.println(propVar + " = null;");
        }
        out.unindent();
        out.println("}");
      }
    }

    private String _getJspPropertyType(PropertyBean property)
    {
      if (property.isMethodExpression())
        return "MethodExpression";

      if (_is12() && property.isMethodBinding())
        return "MethodExpression";

      if (_is12() && !property.isLiteralOnly())
        return "ValueExpression";
      return "String";
    }
  }

  class ValidatorTagGenerator
  {
    public void generateTagHandler(
      ValidatorBean validator)
    {
      String fullClassName = validator.getTagClass();

      try
      {
        getLog().debug("Generating " + fullClassName);
        String sourcePath = Util.convertClassToSourcePath(fullClassName, ".java");
        File targetFile = new File(generatedSourceDirectory, sourcePath);

        targetFile.getParentFile().mkdirs();
        StringWriter sw = new StringWriter();
        PrettyWriter out = new PrettyWriter(sw);

        String className = Util.getClassFromFullClass(fullClassName);
        String packageName = Util.getPackageFromFullClass(fullClassName);

        // header/copyright
        writePreamble(out);

        // package
        out.println("package " + packageName + ";");

        out.println();
        _writeImports(out, validator);

        out.println("/**");
        // TODO: remove this blank line.
        out.println();
        out.println(" * Auto-generated tag class.");
        out.println(" */");

        if (_is12())
        {
          out.println("public class " + className +
                      " extends ValidatorELTag");
        }
        else
        {
          out.println("public class " + className +
                      " extends ValidatorTag");
        }

        out.println("{");
        out.indent();

        _writeConstructor(out, validator);

        _writePropertyMethods(out, validator);
        _writeDoStartTag(out, validator);
        _writeCreateValidator(out, validator);
        _writeSetProperties(out, validator);
        _writeRelease(out, validator);

        out.unindent();
        out.println("}");
        out.close();

        // delay write in case of error
        // timestamp should not be updated when an error occurs
        // delete target file first, because it is readonly
        targetFile.delete();
        FileWriter fw = new FileWriter(targetFile);
        StringBuffer buf = sw.getBuffer();
        fw.write(buf.toString());
        fw.close();
        targetFile.setReadOnly();
      }
      catch (Throwable e)
      {
        getLog().error("Error generating " + fullClassName, e);
      }
    }

    private void _writeImports(
      PrettyWriter   out,
      ValidatorBean  validator)
    {
      Set imports = new TreeSet();

      if (_is12())
      {
        imports.add("javax.faces.webapp.ValidatorELTag");
        imports.add("javax.faces.context.FacesContext");
        imports.add("javax.faces.application.Application");
      }
      else
        imports.add("javax.faces.webapp.ValidatorTag");

      imports.add("javax.servlet.jsp.JspException");
      imports.add(validator.getValidatorClass());

      Iterator properties = validator.properties();
      properties = new FilteredIterator(properties, new TagAttributeFilter());
      if (properties.hasNext())
      {
        imports.add("javax.faces.validator.Validator");
        if (_is12())
          imports.add("javax.el.ValueExpression");
        else
          imports.add("javax.faces.el.ValueBinding");
        imports.add("org.apache.myfaces.trinidadinternal.taglib.util.TagUtils");
      }

      while (properties.hasNext())
      {
        PropertyBean property = (PropertyBean)properties.next();

        String propertyClass = property.getPropertyClass();
        if (propertyClass != null)
          imports.add(propertyClass);

        if ("java.lang.String[]".equals(propertyClass))
        {
          imports.add("java.text.ParseException");
        }
      }

      // do not import implicit!
      imports.removeAll(Util.PRIMITIVE_TYPES);

      String tagClass = validator.getTagClass();
      String packageName = Util.getPackageFromFullClass(tagClass);
      writeImports(out, packageName, imports);
    }

    private void _writeConstructor(
      PrettyWriter  out,
      ValidatorBean validator) throws IOException
    {
      String fullClassName = validator.getTagClass();
      String className = Util.getClassFromFullClass(fullClassName);
      out.println();
      out.println("/**");
      // TODO: restore this correctly phrased comment (tense vs. command)
      //out.println(" * Constructs an instance of " + className + ".");
      out.println(" * Construct an instance of the " + className + ".");
      out.println(" */");
      out.println("public " + className + "()");
      out.println("{");
      out.println("}");
    }

    private void _writePropertyMethods(
      PrettyWriter  out,
      ValidatorBean validator) throws IOException
    {
      Iterator properties = validator.properties();
      properties = new FilteredIterator(properties, new TagAttributeFilter());
      while (properties.hasNext())
      {
        PropertyBean property = (PropertyBean)properties.next();
        out.println();
        _writePropertyMember(out, property);
        _writePropertySet(out, property);
      }
    }

    private void _writePropertyMember(
     PrettyWriter  out,
     PropertyBean  property) throws IOException
    {
      String propName = property.getPropertyName();
      String propVar = "_" + Util.getVariableFromName(propName);
      String jspPropType = _getJspPropertyType(property);

      out.println("private " + jspPropType + " " + propVar + ";");
    }

    private void _writePropertySet(
     PrettyWriter  out,
     PropertyBean  property) throws IOException
    {
      String propName = property.getPropertyName();
      String propVar = Util.getVariableFromName(propName);
      String setMethod = Util.getPrefixedPropertyName("set", propName);
      String jspPropType = _getJspPropertyType(property);

      // TODO: restore coding standards, and make final
      out.println("public void " + setMethod + "(" + jspPropType + " " + propVar + ")");
      out.println("{");
      out.indent();
      out.println("_" + propVar + " = " + propVar + ";");
      out.unindent();
      out.println("}");
    }

    private void _writeDoStartTag(
      PrettyWriter  out,
      ValidatorBean validator) throws IOException
    {
      out.println();
      if (!_is12())
      {
        String validatorFullClass = validator.getValidatorClass();
        String validatorClass = Util.getClassFromFullClass(validatorFullClass);

        // TODO: restore coding standards, and make final
        out.println("@Override");
        out.println("public int doStartTag() throws JspException");
        out.println("{");
        out.indent();
        out.println("super.setValidatorId(" + validatorClass + ".VALIDATOR_ID);");
        out.println("return super.doStartTag();");
        out.unindent();
        out.println("}");
      }
    }

    private void _writeCreateValidator(
      PrettyWriter  out,
      ValidatorBean validator) throws IOException
    {
      Iterator properties = validator.properties();
      properties = new FilteredIterator(properties, new TagAttributeFilter());
      if (properties.hasNext())
      {
        String validatorFullClass = validator.getValidatorClass();
        String validatorClass = Util.getClassFromFullClass(validatorFullClass);

        out.println();
        // TODO: restore coding standards, and make final
        out.println("@Override");
        out.println("protected Validator createValidator() throws JspException");
        out.println("{");
        out.indent();
        if (_is12())
        {
          out.println("String validatorId = " + validatorClass + ".VALIDATOR_ID;");
          out.println("Application appl = FacesContext.getCurrentInstance().getApplication();");
          out.println(validatorClass + " validator = " +
                      "(" + validatorClass + ")appl.createValidator(validatorId);");
        }
        else
        {
          out.println(validatorClass + " validator = " +
                      "(" + validatorClass + ")super.createValidator();");
        }
        out.println("_setProperties(validator);");
        out.println("return validator;");
        out.unindent();
        out.println("}");
      }
    }

    private void _writeSetProperties(
      PrettyWriter  out,
      ValidatorBean validator) throws IOException
    {
      Iterator properties = validator.properties();
      properties = new FilteredIterator(properties, new TagAttributeFilter());
      if (properties.hasNext())
      {
        String validatorFullClass = validator.getValidatorClass();
        String validatorClass = Util.getClassFromFullClass(validatorFullClass);
        out.println();
        out.println("private void _setProperties(");
        out.indent();
        out.println(validatorClass + " validator) throws JspException");
        out.unindent();
        out.println("{");
        out.indent();
        while (properties.hasNext())
        {
          PropertyBean property = (PropertyBean)properties.next();
          _writeSetProperty(out, property);
        }
        out.unindent();
        out.println("}");
      }
    }

    private void _writeSetProperty(
      PrettyWriter out,
      PropertyBean property)
    {
      String propName = property.getPropertyName();
      String propFullClass = property.getPropertyClass();
      String propClass = Util.getClassFromFullClass(propFullClass);
      String propVar = "_" + Util.getVariableFromName(propName);
      out.println("if (" + propVar + " != null)");
      out.println("{");
      out.indent();
      if (_is12())
      {
        out.println("if (!" + propVar + ".isLiteralText())");
        out.println("{");
        out.indent();
        out.println("validator.setValueExpression(\"" + propName + "\", " +
                    propVar + ");");
        out.unindent();
        out.println("}");
        String propType = _resolveType(propFullClass);
        if (propType != null)
        {
          out.println("else");
          out.println("{");
          out.indent();
          if ("StringArray".equals(propType))
          {
            out.println("try");
            out.println("{");
          }
          
          out.println(propClass + " value = TagUtils.get" + propType + "(" + propVar + ".getValue(null));");
          String setMethod = Util.getPrefixedPropertyName("set", propName);
          out.println("validator." + setMethod + "(value);");
          if ("StringArray".equals(propType))
          {
            out.println("}");
            out.println("catch (ParseException pe)");
            out.println("{");
            out.indent();
            out.println("throw new JspException(");
            out.println("  pe.getMessage() + \": \" + \"Position \" + pe.getErrorOffset());");
            out.unindent();
            out.println("}");
          }
          out.unindent();
          out.println("}");
        }
      }
      else
      {
        out.println("if (TagUtils.isValueReference(" + propVar + "))");
        out.println("{");
        out.indent();
        out.println("ValueBinding vb = TagUtils.getValueBinding(" + propVar + ");");
        out.println("validator.setValueBinding(\"" + propName + "\", vb);");
        out.unindent();
        out.println("}");
        String propType = _resolveType(propFullClass);
        if (propType != null)
        {
          out.println("else");
          out.println("{");
          out.indent();
          if ("StringArray".equals(propType))
          {
            out.println("try");
            out.println("{");
          }
          out.println(propClass + " value = TagUtils.get" + propType + "(" + propVar + ");");
          String setMethod = Util.getPrefixedPropertyName("set", propName);
          out.println("validator." + setMethod + "(value);");
          if ("StringArray".equals(propType))
          {
            out.println("}");
            out.println("catch (ParseException pe)");
            out.println("{");
            out.indent();
            out.println("throw new JspException(");
            out.println("  pe.getMessage() + \": \" + \"Position \" + pe.getErrorOffset());");
            out.unindent();
            out.println("}");
          }
          out.unindent();
          out.println("}");
        }
      }

      out.unindent();
      out.println("}");
    }

    private void _writeRelease(
      PrettyWriter  out,
      ValidatorBean validator) throws IOException
    {
      Iterator properties = validator.properties();
      properties = new FilteredIterator(properties, new TagAttributeFilter());
      if (properties.hasNext())
      {
        out.println();
        out.println("@Override");
        out.println("public void release()");
        out.println("{");
        out.indent();
        out.println("super.release();");
        while (properties.hasNext())
        {
          PropertyBean property = (PropertyBean)properties.next();
          String propName = property.getPropertyName();
          String propVar = "_" + Util.getVariableFromName(propName);
          out.println(propVar + " = null;");
        }
        out.unindent();
        out.println("}");
      }
    }

    private String _getJspPropertyType(PropertyBean property)
    {
      if (property.isMethodExpression())
        return "MethodExpression";

      if (_is12() && property.isMethodBinding())
        return "MethodExpression";

      if (_is12() && !property.isLiteralOnly())
        return "ValueExpression";
      return "String";
    }
  }

  class ComponentTagHandlerGenerator
  {
    
    private Set initComponentList(ComponentBean component,
                                  String fullSuperclassName)
    {
      Set componentList = new HashSet();
      componentList.add(component);

      ComponentBean lBean = component;
      while ((lBean = lBean.resolveSupertype()) != null &&
             !fullSuperclassName.equals(lBean.getTagClass()))
      {
        getLog().debug(component.getComponentType()+
                       ": Add additional Tags from: " + lBean.getComponentType());
        componentList.add(lBean);
      }

      return componentList;
    }

    public void generateTagHandler(ComponentBean component)
    {
      ComponentTagGenerator generator;
      Set componentList;
      
      String fullSuperclassName = component.findJspTagSuperclass();
      if (fullSuperclassName == null)
      {
        getLog().warn("Missing JSP Tag superclass for component: " + component.getComponentClass()
                      + ", generation of this Tag is skipped");
        return;
      }
      
      componentList = initComponentList(component, fullSuperclassName);
      
      String fullClassName = component.getTagClass();
      try
      {
        getLog().debug("Generating " + fullClassName);
        
        String sourcePath = Util.convertClassToSourcePath(fullClassName, ".java");
        File targetFile = new File(generatedSourceDirectory, sourcePath);
        
        targetFile.getParentFile().mkdirs();
        StringWriter sw = new StringWriter();
        PrettyWriter out = new PrettyWriter(sw);
        
        if (component.isTrinidadComponent())
        {
          generator = new TrinidadComponentTagGenerator(_is12());
        }
        else
        {
          generator = new MyFacesComponentTagGenerator(_is12());
        }
        
        String className = Util.getClassFromFullClass(fullClassName);
        String packageName = Util.getPackageFromFullClass(fullClassName);
        
        // header/copyright
        writePreamble(out);
        
        // package
        out.println("package " + packageName + ";");
        
        out.println();
        
        String superclassName = Util.getClassFromFullClass(fullSuperclassName);
        if (superclassName.equals(className))
        {
          superclassName = fullSuperclassName;
        }
        String componentFullClass = component.getComponentClass();
        String componentClass = Util.getClassFromFullClass(componentFullClass);
        
        generator.writeImports(out, null, packageName, fullSuperclassName, superclassName, componentList);
        
        generator.writeClassBegin(out, className, superclassName, component, null);
        
        int modifiers = component.getTagClassModifiers();
        generator.writeConstructor(out, component, modifiers);
        
        
        if (!Modifier.isAbstract(modifiers))
        {
          generator.writeGetComponentType(out, component);
          generator.writeGetRendererType(out, component);
        }

        GenerateJspTaglibsMojo.this.writeCustomComponentTagHandlerContent(out, component);

        generator.writePropertyMembers(out, componentList);
        generator.writeSetPropertiesMethod(out, componentClass, componentList);
        generator.writeReleaseMethod(out, componentList);
        
        generator.writeClassEnd(out);
        out.close();
        
        // delay write in case of error
        // timestamp should not be updated when an error occurs
        // delete target file first, because it is readonly
        targetFile.delete();
        FileWriter fw = new FileWriter(targetFile);
        StringBuffer buf = sw.getBuffer();
        fw.write(buf.toString());
        fw.close();
        targetFile.setReadOnly();
      }
      catch (Throwable e)
      {
        getLog().error("Error generating " + fullClassName, e);
      }
    }
  }

  protected boolean is12()
  {
    return "1.2".equals(jsfVersion) || "12".equals(jsfVersion);
  }

  private boolean _is12()
  {
    return is12();
  }

  private class IfComponentModifiedFilter extends ComponentFilter
  {
    protected boolean accept(
      ComponentBean component)
    {
      String tagClass = component.getTagClass();
      String sourcePath = Util.convertClassToSourcePath(tagClass, ".java");
      String templatePath = Util.convertClassToSourcePath(tagClass, "Template.java");
      File targetFile = new File(generatedSourceDirectory, sourcePath);
      File templateFile = new File(templateSourceDirectory, templatePath);

      // accept if templateFile is newer or component has been modified
      return (templateFile.lastModified() > targetFile.lastModified() ||
              component.isModifiedSince(targetFile.lastModified()));
    }
  }

  private class IfConverterModifiedFilter extends ConverterFilter
  {
    protected boolean accept(
      ConverterBean converter)
    {
      String tagClass = converter.getTagClass();
      String sourcePath = Util.convertClassToSourcePath(tagClass, ".java");
      String templatePath = Util.convertClassToSourcePath(tagClass, "Template.java");
      File targetFile = new File(generatedSourceDirectory, sourcePath);
      File templateFile = new File(templateSourceDirectory, templatePath);

      // accept if templateFile is newer or component has been modified
      return (templateFile.lastModified() > targetFile.lastModified() ||
              converter.isModifiedSince(targetFile.lastModified()));
    }
  }

  private class IfValidatorModifiedFilter extends ValidatorFilter
  {
    protected boolean accept(
      ValidatorBean validator)
    {
      String tagClass = validator.getTagClass();
      String sourcePath = Util.convertClassToSourcePath(tagClass, ".java");
      String templatePath = Util.convertClassToSourcePath(tagClass, "Template.java");
      File targetFile = new File(generatedSourceDirectory, sourcePath);
      File templateFile = new File(templateSourceDirectory, templatePath);

      // accept if templateFile is newer or component has been modified
      return (templateFile.lastModified() > targetFile.lastModified() ||
              validator.isModifiedSince(targetFile.lastModified()));
    }
  }

  /**
   * @parameter expression="${project}"
   * @required
   * @readonly
   */
  protected MavenProject project;

  /**
   * @parameter
   * @required
   */
  protected Map taglibs;

  /**
   * @parameter expression="META-INF/maven-faces-plugin/faces-config.xml"
   * @required
   * @readonly
   */
  protected String resourcePath;

  /**
   * @parameter expression="src/main/conf"
   * @required
   */
  protected File configSourceDirectory;

  /**
   * @parameter expression="src/main/java-templates"
   * @required
   */
  protected File templateSourceDirectory;

  /**
   * @parameter expression="${project.build.directory}/maven-faces-plugin/main/java"
   * @required
   */
  protected File generatedSourceDirectory;

  /**
   * @parameter expression="${project.build.directory}/maven-faces-plugin/main/resources"
   * @required
   */
  protected File generatedResourcesDirectory;

  /**
   * @parameter
   */
  protected String packageContains = "";

  /**
   * @parameter
   */
  protected boolean force;


  /**
   * @parameter
   */
  protected boolean disableIdExpressions;

  /**
   * @parameter
   */
  protected boolean coerceStrings;

  
  /**
   * @parameter
   */
  private String jsfVersion;

  static private String _resolveType(
    String className)
  {
    return (String)_RESOLVABLE_TYPES.get(className);
  }

  // TODO: for everything but Locale, String[], Date, and TimeZone,
  // in JSF 1.2 we should already be going through coercion, and
  // not need any of the "TagUtils" functions
  static private Map _createResolvableTypes()
  {
    Map resolvableTypes = new HashMap();

    resolvableTypes.put("boolean", "Boolean");
    resolvableTypes.put("char", "Character");
    resolvableTypes.put("java.util.Date", "Date");
    resolvableTypes.put("int", "Integer");
    resolvableTypes.put("float", "Float");
    resolvableTypes.put("double", "Double");
    resolvableTypes.put("java.util.Locale", "Locale");
    resolvableTypes.put("long", "Long");
    resolvableTypes.put("java.lang.String", "String");
    resolvableTypes.put("java.lang.String[]", "StringArray");
    resolvableTypes.put("java.util.TimeZone", "TimeZone");

    return Collections.unmodifiableMap(resolvableTypes);
  }

  static final private Map _RESOLVABLE_TYPES = _createResolvableTypes();

  static final private String _JSP_TAG_LIBRARY_DOCTYPE_PUBLIC =
              "-//Sun Microsystems, Inc.//DTD JSP Tag Library 1.2//EN";

  static final private String _JSP_TAG_LIBRARY_DOCTYPE_SYSTEM =
              "http://java.sun.com/dtd/web-jsptaglibrary_1_2.dtd";

  static final private String _JSP_TAG_LIBRARY_DTD =
    "<!DOCTYPE taglib PUBLIC \n" +
    "  \"" + _JSP_TAG_LIBRARY_DOCTYPE_PUBLIC + "\"\n" +
    "  \"" + _JSP_TAG_LIBRARY_DOCTYPE_SYSTEM + "\" >\n";

  static final private String _XINCLUDE_JSP_TAG_LIBRARY_DTD =
    "<!DOCTYPE taglib PUBLIC\n" +
    "  \"" + _JSP_TAG_LIBRARY_DOCTYPE_PUBLIC + "\"\n" +
    "  \"" + _JSP_TAG_LIBRARY_DOCTYPE_SYSTEM + "\" [\n" +
    "      <!ELEMENT xi:include EMPTY>\n" +
    "      <!ATTLIST xi:include\n" +
    "          xmlns:xi CDATA #FIXED  \"" + XIncludeFilter.XINCLUDE_NAMESPACE + "\"\n" +
    "          href     CDATA #IMPLIED\n" +
    "          xpointer CDATA #IMPLIED>\n" +
    "]>\n";

  static final private Set _CAN_COERCE = new HashSet();
  static
  {
    // What?  Can't coerce Strings?  How could that be?  Well, take a look at:
    //   http://issues.apache.org/jira/browse/ADFFACES-377
    // The silly coercion rules in JSP convert null to the
    // empty string.  So it's not that we can't coerce to
    // String, we just really, really don't want to.
    //    _CAN_COERCE.add("java.lang.String");
    // TODO: consider getting rid of coercion rules for
    // all non-primitives
    _CAN_COERCE.add("java.lang.Integer");
    _CAN_COERCE.add("java.lang.Long");
    _CAN_COERCE.add("java.lang.Boolean");
    _CAN_COERCE.add("java.lang.Double");
    _CAN_COERCE.add("java.lang.Float");
    _CAN_COERCE.add("java.lang.Short");
    _CAN_COERCE.add("java.lang.Character");
    _CAN_COERCE.add("java.lang.Byte");
    _CAN_COERCE.add("int");
    _CAN_COERCE.add("long");
    _CAN_COERCE.add("boolean");
    _CAN_COERCE.add("double");
    _CAN_COERCE.add("float");
    _CAN_COERCE.add("short");
    _CAN_COERCE.add("char");
    _CAN_COERCE.add("byte");

    // See http://issues.apache.org/jira/browse/ADFFACES-477:  for
    // "binding" and "converter" properties, we want the deferred-types
    // there.  Hardcoding these here is very ugly, but putting in
    // all coercion rules would break how Trinidad handles Dates,
    // and Lists of Colors, etc. - for those, we want to support
    // raw Strings (which the JSP engine doesn't know how to coerce)
    // and coerce them ourselves in the tag.
    _CAN_COERCE.add("javax.faces.component.UIComponent");
    _CAN_COERCE.add("javax.faces.convert.Converter");
  }
}
