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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.util.Iterator;
import java.util.Map;

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
import org.apache.myfaces.trinidadbuild.plugin.faces.generator.taglib.TagAttributeFilter;
import org.apache.myfaces.trinidadbuild.plugin.faces.parse.ComponentBean;
import org.apache.myfaces.trinidadbuild.plugin.faces.parse.ConverterBean;
import org.apache.myfaces.trinidadbuild.plugin.faces.parse.FacesConfigBean;
import org.apache.myfaces.trinidadbuild.plugin.faces.parse.MethodSignatureBean;
import org.apache.myfaces.trinidadbuild.plugin.faces.parse.PropertyBean;
import org.apache.myfaces.trinidadbuild.plugin.faces.parse.ValidatorBean;
import org.apache.myfaces.trinidadbuild.plugin.faces.util.FilteredIterator;
import org.apache.myfaces.trinidadbuild.plugin.faces.util.XIncludeFilter;

import org.codehaus.plexus.util.FileUtils;

import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

/**
 * @version $Id$
 * @requiresDependencyResolution compile
 * @goal generate-facelets-taglibs
 * @phase generate-sources
 */
public class GenerateFaceletsTaglibsMojo extends AbstractFacesMojo
{
  /**
   * Execute the Mojo.
   */
  public void execute() throws MojoExecutionException
  {
    try
    {
      // always add resources directory to project resource root
      addResourceRoot(project, generatedResourcesDirectory.getCanonicalPath());

      processIndex(project, resourcePath);

      // taglibs map syntax requires distinct shortNames,
      // which is a Good Thing!
      for (Iterator i = taglibs.entrySet().iterator(); i.hasNext(); )
      {
        Map.Entry entry = (Map.Entry)i.next();
        String shortName = (String)entry.getKey();
        String namespaceURI = (String)entry.getValue();

        FacesConfigBean facesConfig = getFacesConfig();
        Iterator<ComponentBean> components = facesConfig.components();
        components = new FilteredIterator<ComponentBean>(components, new SkipFilter());
        components = new FilteredIterator<ComponentBean>(components,
                                                        new ComponentTagLibraryFilter(namespaceURI, false));

        Iterator<ValidatorBean> validators = facesConfig.validators();
        validators = new FilteredIterator<ValidatorBean>(validators,
                                                         new ValidatorTagLibraryFilter(namespaceURI, false));

        Iterator<ConverterBean> converters = facesConfig.converters();
        converters = new FilteredIterator<ConverterBean>(converters,
                                                         new ConverterTagLibraryFilter(namespaceURI, false));


        String targetPath = "META-INF/" + shortName + ".taglib.xml";
        File targetFile = new File(generatedResourcesDirectory, targetPath);

        String configPath = "META-INF/" + shortName + "-base.taglib.xml";
        File configFile = new File(configSourceDirectory, configPath);

        targetFile.delete();

        if ((components.hasNext()||validators.hasNext()||converters.hasNext())
            && configFile.exists())
        {
          ByteArrayOutputStream out = new ByteArrayOutputStream();
          XMLOutputFactory outputFactory = XMLOutputFactory.newInstance();
          XMLStreamWriter stream = outputFactory.createXMLStreamWriter(out);

          _writeStartTagLibrary(stream, _XINCLUDE_FACELETS_TAG_LIBRARY_DTD);
          // base goes first
          stream.writeStartElement("xi", "include",
                                   XIncludeFilter.XINCLUDE_NAMESPACE);
          stream.writeNamespace("xi", XIncludeFilter.XINCLUDE_NAMESPACE);
          stream.writeAttribute("href", configFile.toURI().toURL().toExternalForm());
          stream.writeAttribute("xpointer", "/facelet-taglib/*");
          stream.writeEndElement();
          _writeTags(components, validators, converters, stream);

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
          Result mergedResult = new StreamResult(targetFile);

          TransformerFactory transFactory = TransformerFactory.newInstance();
          Transformer identity = transFactory.newTransformer();
          if (!_isJSF20PLus())
          {
            identity.setOutputProperty(OutputKeys.DOCTYPE_PUBLIC,
                                       _FACELETS_TAG_LIBRARY_DOCTYPE_PUBLIC);
            identity.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM,
                                       _FACELETS_TAG_LIBRARY_DOCTYPE_SYSTEM);
          }
          identity.transform(mergedSource, mergedResult);

          targetFile.setReadOnly();
        }
        else if (components.hasNext()||validators.hasNext()||converters.hasNext())
        {
          targetFile.getParentFile().mkdirs();
          OutputStream out = new FileOutputStream(targetFile);
          XMLOutputFactory outputFactory = XMLOutputFactory.newInstance();
          XMLStreamWriter stream = outputFactory.createXMLStreamWriter(out);

          _writeStartTagLibrary(stream, _FACELETS_TAG_LIBRARY_DTD);
          stream.writeCharacters("\n  ");
          stream.writeStartElement("namespace");
          stream.writeCharacters(namespaceURI);
          stream.writeEndElement();

          _writeTags(components, validators, converters, stream);
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

        getLog().info("Generated " + targetPath);
      }
    }
    catch (XMLStreamException e)
    {
      throw new MojoExecutionException("Error during generation", e);
    }
    catch (SAXException e)
    {
      throw new MojoExecutionException("Error during generation", e);
    }
    catch (TransformerException e)
    {
      throw new MojoExecutionException("Error during generation", e);
    }
    catch (ParserConfigurationException e)
    {
      throw new MojoExecutionException("Error during generation", e);
    }
    catch (IOException e)
    {
      throw new MojoExecutionException("Error during generation", e);
    }
  }

  private void _writeTags(Iterator components, Iterator validators, Iterator converters, XMLStreamWriter stream) throws XMLStreamException {
    while (components.hasNext())
    {
      ComponentBean component = (ComponentBean)components.next();
      _writeTag(stream, component);
    }
    while (validators.hasNext())
    {
      _writeValidatorTag(stream, (ValidatorBean) validators.next());
    }
    while (converters.hasNext())
    {
      _writeConverterTag(stream, (ConverterBean) converters.next());
    }
  }

  private void _writeStartTagLibrary(
    XMLStreamWriter stream,
    String          dtd) throws XMLStreamException
  {
    stream.writeStartDocument("1.0");
    stream.writeCharacters("\n");

    boolean isJSF20PLus = _isJSF20PLus();

    String ns = _FACELETS_NAMESPACE_URI_20;

    if (!isJSF20PLus)
    {
      ns = _FACELETS_NAMESPACE_URI;
      stream.writeDTD(dtd);
      stream.writeCharacters("\n");
    }

    stream.writeStartElement("facelet-taglib");
    stream.writeDefaultNamespace(ns);

    if (isJSF20PLus)
    {
      stream.writeNamespace("xsi", "http://www.w3.org/2001/XMLSchema-instance");
      stream.writeAttribute("http://www.w3.org/2001/XMLSchema-instance", "schemaLocation",
                            "http://java.sun.com/xml/ns/javaee http://java.sun.com/xml/ns/javaee/web-facelettaglibrary_2_0.xsd");
      stream.writeAttribute("version", "2.0");
    }
    stream.writeCharacters("\n  ");
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

    boolean isJSF20PLus = _isJSF20PLus();

    if (isJSF20PLus && component.getDescription() != null)
    {
      stream.writeCharacters("\n    ");
      stream.writeStartElement("description");
      stream.writeCData(component.getDescription());
      stream.writeEndElement();
    }

    stream.writeCharacters("\n    ");
    stream.writeStartElement("tag-name");
    stream.writeCharacters(component.getTagName().getLocalPart());
    stream.writeEndElement();
    stream.writeCharacters("\n    ");
    stream.writeStartElement("component");
    stream.writeCharacters("\n      ");
    stream.writeStartElement("component-type");
    stream.writeCharacters(component.getComponentType());
    stream.writeEndElement();

    if (component.getRendererType() != null)
    {
      stream.writeCharacters("\n      ");
      stream.writeStartElement("renderer-type");
      stream.writeCharacters(component.getRendererType());
      stream.writeEndElement();
    }

    String tagHandler = component.getTagHandler();
    if (tagHandler == null)
    {
      tagHandler = faceletHandlerClass;
    }
    if (tagHandler != null)
    {
      stream.writeCharacters("\n      ");
      stream.writeStartElement("handler-class");
      stream.writeCharacters(tagHandler);
      stream.writeEndElement();
    }

    stream.writeCharacters("\n    ");
    stream.writeEndElement();

    if (isJSF20PLus)
    {
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
    }

    stream.writeCharacters("\n  ");
    stream.writeEndElement();
  }

  /**
   * Generates tag library descriptor for parsed validator metadata.
   */
  private void _writeValidatorTag(
    XMLStreamWriter stream,
    ValidatorBean   validator) throws XMLStreamException
  {
    stream.writeCharacters("\n  ");
    stream.writeStartElement("tag");

    boolean isJSF20PLus = _isJSF20PLus();

    if (isJSF20PLus && validator.getDescription() != null)
    {
      stream.writeCharacters("\n    ");
      stream.writeStartElement("description");
      stream.writeCData(validator.getDescription());
      stream.writeEndElement();
    }

    stream.writeCharacters("\n    ");
    stream.writeStartElement("tag-name");
    stream.writeCharacters(validator.getTagName().getLocalPart());
    stream.writeEndElement();
    stream.writeCharacters("\n    ");
    stream.writeStartElement("validator");
    stream.writeCharacters("\n      ");
    stream.writeStartElement("validator-id");
    String id = validator.getRootValidatorId() == null ?
                validator.getValidatorId() : validator.getRootValidatorId();
    stream.writeCharacters(id);
    stream.writeEndElement();

    String tagHandler = faceletValidatorHandlerClass;

    if (tagHandler != null)
    {
      stream.writeCharacters("\n      ");
      stream.writeStartElement("handler-class");
      stream.writeCharacters(tagHandler);
      stream.writeEndElement();
    }

    stream.writeCharacters("\n    ");
    stream.writeEndElement();

    if (isJSF20PLus)
    {
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
    }

    stream.writeCharacters("\n  ");
    stream.writeEndElement();
  }

  /**
   * Generates tag library descriptor for parsed converter metadata.
   */
  private void _writeConverterTag(
    XMLStreamWriter stream,
    ConverterBean   converter) throws XMLStreamException
  {
    stream.writeCharacters("\n  ");
    stream.writeStartElement("tag");

    boolean isJSF20PLus = _isJSF20PLus();

    if (isJSF20PLus && converter.getDescription() != null)
    {
      stream.writeCharacters("\n    ");
      stream.writeStartElement("description");
      stream.writeCData(converter.getDescription());
      stream.writeEndElement();
    }

    stream.writeCharacters("\n    ");
    stream.writeStartElement("tag-name");
    stream.writeCharacters(converter.getTagName().getLocalPart());
    stream.writeEndElement();
    stream.writeCharacters("\n    ");
    stream.writeStartElement("converter");
    stream.writeCharacters("\n      ");
    stream.writeStartElement("converter-id");
    String id = converter.getRootConverterId() == null ?
                converter.getConverterId() : converter.getRootConverterId();
    stream.writeCharacters(id);
    stream.writeEndElement();

    String tagHandler = faceletConverterHandlerClass;

    if (tagHandler != null)
    {
      stream.writeCharacters("\n      ");
      stream.writeStartElement("handler-class");
      stream.writeCharacters(tagHandler);
      stream.writeEndElement();
    }

    stream.writeCharacters("\n    ");
    stream.writeEndElement();

    if (isJSF20PLus)
    {
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
    }

    stream.writeCharacters("\n  ");
    stream.writeEndElement();
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

      _writeTagAttributeDescription(stream, description, unsupportedAgents);

      stream.writeCharacters("\n      ");
      stream.writeStartElement("name");

      if (property != null)
        stream.writeCharacters(property.getJspPropertyName());
      else
        stream.writeCharacters(propertyName);

      stream.writeEndElement();

      if (property != null)
      {
        if (property.isRequired())
        {
          stream.writeCharacters("\n    ");
          stream.writeStartElement("required");
          stream.writeCharacters("true");
          stream.writeEndElement();
        }

        MethodSignatureBean sig = null;
        if ((property.isMethodExpression() || property.isMethodBinding()) && (sig = property.getMethodBindingSignature()) != null)
        {
          stream.writeCharacters("\n      ");
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
        else
        {
          String propertyClass = property.getPropertyClass();
          stream.writeCharacters("\n      ");
          stream.writeStartElement("type");
          stream.writeCharacters(propertyClass);
          stream.writeEndElement();
        }

      }

      stream.writeCharacters("\n    ");
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

    private boolean _isJSF20PLus()
    {
      return !(JsfVersion.isJSF11(jsfVersion) || JsfVersion.isJSF12(jsfVersion));
    }


  /**
   * @parameter expression="${project}"
   * @required
   * @readonly
   */
  private MavenProject project;

  /**
   * @parameter expression="META-INF/maven-faces-plugin/faces-config.xml"
   * @required
   * @readonly
   */
  private String resourcePath;

  /**
   * @parameter
   * @required
   */
  private Map taglibs;


  /**
   *@parameter
   */
  private String faceletHandlerClass;

  /**
   *@parameter
   */
  private String faceletConverterHandlerClass;

  /**
   *@parameter
   */
  private String faceletValidatorHandlerClass;

  /**
   * @parameter expression="src/main/conf"
   * @required
   */
  private File configSourceDirectory;

  /**
   * @parameter expression="${project.build.directory}/maven-faces-plugin/main/resources"
   * @required
   */
  private File generatedResourcesDirectory;

  /**
   * @parameter
   */
  private boolean force;

  /**
   * @parameter
   */
  private String jsfVersion;

  static private final String _FACELETS_NAMESPACE_URI =
              "http://java.sun.com/JSF/Facelet";

  static private final String _FACELETS_NAMESPACE_URI_20 =
              "http://java.sun.com/xml/ns/javaee";

  static final private String _FACELETS_TAG_LIBRARY_DOCTYPE_PUBLIC =
              "-//Sun Microsystems, Inc.//DTD Facelet Taglib 1.0//EN";

  static final private String _FACELETS_TAG_LIBRARY_DOCTYPE_SYSTEM =
              "http://java.sun.com/dtd/facelet-taglib_1_0.dtd";

  static final private String _FACELETS_TAG_LIBRARY_DTD =
    "<!DOCTYPE facelet-taglib PUBLIC \n" +
    "  \"" + _FACELETS_TAG_LIBRARY_DOCTYPE_PUBLIC + "\"\n" +
    "  \"" + _FACELETS_TAG_LIBRARY_DOCTYPE_SYSTEM + "\" >\n";

  static final private String _XINCLUDE_FACELETS_TAG_LIBRARY_DTD =
    "<!DOCTYPE taglib PUBLIC\n" +
    "  \"" + _FACELETS_TAG_LIBRARY_DOCTYPE_PUBLIC + "\"\n" +
    "  \"" + _FACELETS_TAG_LIBRARY_DOCTYPE_SYSTEM + "\" [\n" +
    "      <!ELEMENT xi:include EMPTY>\n" +
    "      <!ATTLIST xi:include\n" +
    "          xmlns:xi CDATA #FIXED  \"" + XIncludeFilter.XINCLUDE_NAMESPACE + "\"\n" +
    "          href     CDATA #IMPLIED\n" +
    "          xpointer CDATA #IMPLIED>\n" +
    "]>\n";
}
