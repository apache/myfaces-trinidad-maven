/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.myfaces.trinidadbuild.plugin.faces;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;

import java.lang.reflect.Modifier;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.myfaces.trinidadbuild.plugin.faces.generator.taglib.AbstractConverterTagGenerator;
import org.apache.myfaces.trinidadbuild.plugin.faces.generator.taglib.AbstractValidatorTagGenerator;
import org.apache.myfaces.trinidadbuild.plugin.faces.generator.taglib.ComponentTagGenerator;
import org.apache.myfaces.trinidadbuild.plugin.faces.generator.taglib.MyFacesComponentTagGenerator;
import org.apache.myfaces.trinidadbuild.plugin.faces.generator.taglib.MyFacesConverterTagGenerator;
import org.apache.myfaces.trinidadbuild.plugin.faces.generator.taglib.MyFacesValidatorTagGenerator;
import org.apache.myfaces.trinidadbuild.plugin.faces.generator.taglib.TagAttributeFilter;
import org.apache.myfaces.trinidadbuild.plugin.faces.generator.taglib.TrinidadComponentTagGenerator;
import org.apache.myfaces.trinidadbuild.plugin.faces.generator.taglib.TrinidadConverterTagGenerator;
import org.apache.myfaces.trinidadbuild.plugin.faces.generator.taglib.TrinidadValidatorTagGenerator;
import org.apache.myfaces.trinidadbuild.plugin.faces.io.PrettyWriter;
import org.apache.myfaces.trinidadbuild.plugin.faces.parse.ComponentBean;
import org.apache.myfaces.trinidadbuild.plugin.faces.parse.ConverterBean;
import org.apache.myfaces.trinidadbuild.plugin.faces.parse.FacesConfigBean;
import org.apache.myfaces.trinidadbuild.plugin.faces.parse.MethodSignatureBean;
import org.apache.myfaces.trinidadbuild.plugin.faces.parse.PropertyBean;
import org.apache.myfaces.trinidadbuild.plugin.faces.parse.ValidatorBean;
import org.apache.myfaces.trinidadbuild.plugin.faces.util.Filter;
import org.apache.myfaces.trinidadbuild.plugin.faces.util.FilteredIterator;
import org.apache.myfaces.trinidadbuild.plugin.faces.util.SourceTemplate;
import org.apache.myfaces.trinidadbuild.plugin.faces.util.Util;
import org.apache.myfaces.trinidadbuild.plugin.faces.util.XIncludeFilter;

import org.codehaus.plexus.util.FileUtils;

import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;


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
   @SuppressWarnings("unused") PrettyWriter  out,
   @SuppressWarnings("unused") ComponentBean component
    ) throws IOException
  {
  }

  // hook for custom component tag java imports
  protected void addCustomComponentTagHandlerImports(
    @SuppressWarnings("unused") Set           imports,
    @SuppressWarnings("unused") ComponentBean component)
  {
  }

  // hook for custom component descriptor content
  protected void writeCustomComponentTagDescriptorContent(
   @SuppressWarnings("unused") XMLStreamWriter  stream,
   @SuppressWarnings("unused") ComponentBean    component
    )throws XMLStreamException
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
      for (Iterator<Map.Entry<String, String>> i = taglibs.entrySet().iterator(); i.hasNext(); )
      {
        Map.Entry<String, String> entry = i.next();
        String shortName = entry.getKey();
        String namespaceURI = entry.getValue();

        FacesConfigBean facesConfig = getFacesConfig();
        Iterator<ComponentBean> components = facesConfig.components();
        components = new FilteredIterator<ComponentBean>(components, new SkipFilter());
        components = new FilteredIterator<ComponentBean>(components, new ComponentTagLibraryFilter(namespaceURI));

        Iterator<ValidatorBean> validators = facesConfig.validators();
        validators = new FilteredIterator<ValidatorBean>(validators, new ValidatorTagLibraryFilter(namespaceURI));

        Iterator<ConverterBean> converters = facesConfig.converters();
        converters = new FilteredIterator<ConverterBean>(converters, new ConverterTagLibraryFilter(namespaceURI));

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
          stream.writeAttribute("href", configFile.toURI().toURL().toExternalForm());
          stream.writeAttribute("xpointer", "/taglib/*");
          stream.writeEndElement();
          while (components.hasNext())
          {
            ComponentBean component = components.next();
            _writeTag(stream, component);
          }
          while (converters.hasNext())
          {
            ConverterBean converter = converters.next();
            _writeTag(stream, converter);
          }
          while (validators.hasNext())
          {
            ValidatorBean validator = validators.next();
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
          mergedReader = new XIncludeFilter(mergedReader, configFile.toURI().toURL());
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
          if (JsfVersion.isJSF11(jsfVersion))
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

          _writeStartTagLibrary(stream, !JsfVersion.isJSF11(jsfVersion) ? "2.1" : "1.2", shortName, namespaceURI);
          while (components.hasNext())
          {
            ComponentBean component = components.next();
            _writeTag(stream, component);
          }
          while (converters.hasNext())
          {
            ConverterBean converter = converters.next();
            _writeTag(stream, converter);
          }
          while (validators.hasNext())
          {
            ValidatorBean validator = validators.next();
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
    if (JsfVersion.isJSF11(jsfVersion))
      stream.writeDTD(dtd);

    stream.writeCharacters("\n");
    stream.writeStartElement("taglib");
    if (!JsfVersion.isJSF11(jsfVersion))
    {
      stream.writeNamespace("", "http://java.sun.com/xml/ns/javaee");
      stream.writeNamespace("xsi", "http://www.w3.org/2001/XMLSchema-instance");
      stream.writeAttribute("xsi:schemaLocation",
           "http://java.sun.com/xml/ns/javaee http://java.sun.com/xml/ns/javaee/web-jsptaglibrary_2_1.xsd");
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

    if (JsfVersion.isJSF11(jsfVersion))
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
    if (!JsfVersion.isJSF11(jsfVersion) && component.getDescription() != null)
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
    if (!JsfVersion.isJSF11(jsfVersion))
    {
      stream.writeCharacters("\n    ");
      stream.writeStartElement("body-content");
      stream.writeCharacters("JSP");
      stream.writeEndElement();
    }

    GenerateJspTaglibsMojo.this.writeCustomComponentTagDescriptorContent(stream, component);

    // In JSP 2.0, description goes just before the attributes
    if (JsfVersion.isJSF11(jsfVersion) && component.getDescription() != null)
    {
      stream.writeCharacters("\n    ");
      stream.writeStartElement("description");
      stream.writeCData(component.getDescription());
      stream.writeEndElement();
    }

    Iterator<PropertyBean> properties = component.properties(true);
    properties = new FilteredIterator<PropertyBean>(properties, new TagAttributeFilter());
    while (properties.hasNext())
    {
      PropertyBean property = properties.next();
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
    if (!JsfVersion.isJSF11(jsfVersion) && converter.getDescription() != null)
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
    if (!JsfVersion.isJSF11(jsfVersion))
    {
      stream.writeCharacters("\n    ");
      stream.writeStartElement("body-content");
      stream.writeCharacters("empty");
      stream.writeEndElement();
    }

    if (JsfVersion.isJSF11(jsfVersion) && converter.getDescription() != null)
    {
      stream.writeCharacters("\n    ");
      stream.writeStartElement("description");
      stream.writeCData(converter.getDescription());
      stream.writeEndElement();
    }

    // converters need an id attribute
    writeTagAttribute(stream, "id", "the identifier for the converter", null, null);

    Iterator<PropertyBean> properties = converter.properties();
    properties = new FilteredIterator<PropertyBean>(properties, new TagAttributeFilter());
    while (properties.hasNext())
    {
      PropertyBean property = properties.next();
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
    if (!JsfVersion.isJSF11(jsfVersion))
      _writeTagAttributeDescription(stream, description, unsupportedAgents);

    stream.writeCharacters("\n      ");
    stream.writeStartElement("name");

    if (property != null)
      stream.writeCharacters(property.getJspPropertyName());
    else
      stream.writeCharacters(propertyName);

    stream.writeEndElement();

    if (JsfVersion.isJSF11(jsfVersion))
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
          if (property.isRtexprvalue() || ("id".equals(propertyName) && !disableIdExpressions))
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

    if (!JsfVersion.isJSF11(jsfVersion) && validator.getDescription() != null)
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
    if (!JsfVersion.isJSF11(jsfVersion))
    {
      stream.writeCharacters("\n    ");
      stream.writeStartElement("body-content");
      stream.writeCharacters("empty");
      stream.writeEndElement();
    }

    if (JsfVersion.isJSF11(jsfVersion) && validator.getDescription() != null)
    {
      stream.writeCharacters("\n    ");
      stream.writeStartElement("description");
      stream.writeCData(validator.getDescription());
      stream.writeEndElement();
    }

    // validators need an id attribute
    writeTagAttribute(stream, "id", "the identifier for the validator", null, null);

    Iterator<PropertyBean> properties = validator.properties();
    properties = new FilteredIterator<PropertyBean>(properties, new TagAttributeFilter());
    while (properties.hasNext())
    {
      PropertyBean property = properties.next();
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
  private void _generateTagHandlers() throws IOException, MojoExecutionException {
    // Make sure generated source directory
    // is added to compilation source path
    project.addCompileSourceRoot(generatedSourceDirectory.getCanonicalPath());

    FacesConfigBean facesConfig = getFacesConfig();
    if (!facesConfig.hasComponents() && !facesConfig.hasConverters() && !facesConfig.hasValidators())
    {
      getLog().info("Nothing to generate - no components found");
    }
    else
    {
      Iterator<ComponentBean> components = facesConfig.components();
      components = new FilteredIterator<ComponentBean>(components, new SkipFilter());
      components = new FilteredIterator<ComponentBean>(components, new ComponentTagFilter());
      components = new FilteredIterator<ComponentBean>(components, new ComponentTagClassFilter(packageContains));

      Iterator<ValidatorBean> validators = facesConfig.validators();
      validators = new FilteredIterator<ValidatorBean>(validators, new ValidatorTagFilter());
      validators = new FilteredIterator<ValidatorBean>(validators, new ValidatorTagClassFilter(packageContains));

      Iterator<ConverterBean> converters = facesConfig.converters();
      converters = new FilteredIterator<ConverterBean>(converters, new ConverterTagFilter());
      converters = new FilteredIterator<ConverterBean>(converters, new ConverterTagClassFilter(packageContains));

      // incremental unless forced
      if (!force)
      {
        components = new FilteredIterator<ComponentBean>(components, new IfComponentModifiedFilter());
        converters = new FilteredIterator<ConverterBean>(converters, new IfConverterModifiedFilter());
        validators = new FilteredIterator<ValidatorBean>(validators, new IfValidatorModifiedFilter());
      }

      if (!components.hasNext() && !converters.hasNext() && !validators.hasNext())
      {
        getLog().info("Nothing to generate - all JSP tags are up to date");
      }
      else
      {
        ComponentTagHandlerGenerator componentGen = new ComponentTagHandlerGenerator();
        AbstractConverterTagGenerator converterGen = null;
        AbstractValidatorTagGenerator validatorGen = null;
        if (type == null || "trinidad".equals(type))
        {
          converterGen = new TrinidadConverterTagGenerator(!JsfVersion.isJSF11(jsfVersion), getLicenseHeader(), getLog());
          validatorGen = new TrinidadValidatorTagGenerator(!JsfVersion.isJSF11(jsfVersion), getLicenseHeader(), getLog());
        }
        else
        {
          converterGen = new MyFacesConverterTagGenerator(!JsfVersion.isJSF11(jsfVersion), getLicenseHeader(), getLog());
          validatorGen = new MyFacesValidatorTagGenerator(!JsfVersion.isJSF11(jsfVersion), getLicenseHeader(), getLog());
        }
        int count = 0;
        while (components.hasNext())
        {
          componentGen.generateTagHandler(components.next());
          count++;
        }
        while (converters.hasNext())
        {
          converterGen.generateTagHandler(converters.next(), generatedSourceDirectory);
          count++;
        }
        while (validators.hasNext())
        {
          validatorGen.generateTagHandler(validators.next(), generatedSourceDirectory);
          count++;
        }
        getLog().info("Generated " + count + " JSP tag(s)");
      }
    }
  }

  class ComponentTagHandlerGenerator
  {
    private Set<ComponentBean> initComponentList(ComponentBean component,
                                  String fullSuperclassName)
    {
      Set<ComponentBean> componentList = new HashSet<ComponentBean>();
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
      Set<ComponentBean> componentList;

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
        String className = Util.getClassFromFullClass(fullClassName);
        String packageName = Util.getPackageFromFullClass(fullClassName);

        TemplateFile templateFile = new TemplateFile(fullClassName, packageName, className);
        boolean hasTemplate = templateFile.exists();
        SourceTemplate sourceTemplate = null;
        String overrideClassName = null;
        String sourcePath = templateFile.getSourcePath();

        if (hasTemplate)
        {
          className = templateFile.getClassName();
          fullClassName = templateFile.getFullClassName();
          overrideClassName = className;

          if (!templateFile.isSubclass())
          {
            // Merged template use case
            sourceTemplate = new SourceTemplate(templateFile.getFile());
          }
        }

        getLog().debug("Generating " + fullClassName);

        File targetFile = new File(generatedSourceDirectory, sourcePath);

        targetFile.getParentFile().mkdirs();
        StringWriter sw = new StringWriter();
        PrettyWriter out = new PrettyWriter(sw);

        if (component.isTrinidadComponent())
        {
          generator = new TrinidadComponentTagGenerator(!JsfVersion.isJSF11(jsfVersion));
        }
        else
        {
          generator = new MyFacesComponentTagGenerator(!JsfVersion.isJSF11(jsfVersion));
        }

        getLog().debug("Generating " + fullClassName + ", with generator: " +
                       generator.getClass().getName());

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

        generator.writeImports(out, null, packageName, fullSuperclassName, superclassName,
          componentList);

        generator.writeClassBegin(out, className, superclassName, component,
          sourceTemplate, hasTemplate);

        int modifiers = component.getTagClassModifiers();
        generator.writeConstructor(out, component, overrideClassName, modifiers);

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

        if (templateFile.isSubclass())
        {
          // If there is a sub-class, copy the file to the directory in the compiler path
          File destFile = new File(generatedSourceDirectory, templateFile.getOriginalSourcePath());

          Util.copyFile(templateFile.getFile(), destFile);
          destFile.setReadOnly();
        }
      }
      catch (Throwable e)
      {
        getLog().error("Error generating " + fullClassName, e);
      }
    }
  }

  private class IfComponentModifiedFilter implements Filter<ComponentBean>
  {
    @Override
    public boolean accept(ComponentBean component)
    {
      String tagClass = component.getTagClass();

      TemplateFile templateFile = new TemplateFile(tagClass);
      String sourcePath = Util.convertClassToSourcePath(tagClass, ".java");
      File targetFile = new File(generatedSourceDirectory, sourcePath);

      long templateFileModified = templateFile.lastModified();
      long targetLastMoified = targetFile.lastModified();

      // accept if templateFile is newer or component has been modified
      return templateFileModified > targetLastMoified ||
        component.isModifiedSince(targetLastMoified);
    }
  }

  private class IfConverterModifiedFilter implements Filter<ConverterBean>
  {
    @Override
    public boolean accept(ConverterBean converter)
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

  private class IfValidatorModifiedFilter implements Filter<ValidatorBean>
  {
    @Override
    public boolean accept(ValidatorBean validator)
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

  private class TemplateFile
  {
    private TemplateFile(
      String fullTagClassName)
    {
      this(fullTagClassName,
        Util.getPackageFromFullClass(fullTagClassName),
        Util.getClassFromFullClass(fullTagClassName));
    }

    private TemplateFile(
      String fullTagClassName,
      String packageName,
      String tagClassName)
    {
      _originalSourcePath = Util.convertClassToSourcePath(fullTagClassName, ".java");
      String templatePath = Util.convertClassToSourcePath(fullTagClassName, "Template.java");
      File templateFile = new File(templateSourceDirectory, templatePath);
      // New style, meaning that the template is named the same as the desired tag name and the
      // generator creates the base class of the template with "Partial" pre-pended to the name:
      File subclassFile = new File(templateSourceDirectory, _originalSourcePath);

      boolean templateExists = templateFile.exists();
      boolean subclassExists = subclassFile.exists();
      if (templateExists && subclassExists)
      {
        throw new IllegalStateException(
          String.format("Both a template tag file, '%s' and a subclass file, '%s' exists.",
            templateFile, subclassFile));
      }

      _isSubclass = subclassExists;

      _file = subclassExists ? subclassFile : templateExists ? templateFile : null;

      if (_isSubclass)
      {
        _className = "Partial" + tagClassName;
        _fullClassName = packageName + "." + _className;
        _sourcePath = Util.convertClassToSourcePath(_fullClassName, ".java");
      }
      else
      {
        _className = tagClassName;
        _fullClassName = fullTagClassName;
        _sourcePath = _originalSourcePath;
      }
    }

    final String getSourcePath()
    {
      return _sourcePath;
    }

    final boolean isSubclass()
    {
      return _isSubclass;
    }

    final boolean exists()
    {
      return _file != null;
    }

    final long lastModified()
    {
      if (_lastModified == -1)
      {
        _lastModified = _file == null ? 0 : _file.lastModified();
      }

      return _lastModified;
    }

    final File getFile()
    {
      return _file;
    }

    final String getClassName()
    {
      return _className;
    }

    final String getFullClassName()
    {
      return _fullClassName;
    }

    final String getOriginalSourcePath()
    {
      return _originalSourcePath;
    }

    private long _lastModified = -1;
    final private String _className;
    final private String _fullClassName;
    final private String _sourcePath;
    final private String _originalSourcePath;
    final private File _file;
    final private boolean _isSubclass;
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
  protected Map<String, String> taglibs;

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
   * @deprecated
   */
  @Deprecated
  protected boolean disableIdExpressions;

  /**
   * @parameter
   */
  protected boolean coerceStrings;


  /**
   * @parameter
   */
  private String jsfVersion;

  /**
   * @parameter expression="trinidad"
   */
  private String type;



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

  static final private Set<String> _CAN_COERCE = new HashSet<String>();
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
