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
package org.apache.myfaces.trinidadbuild.plugin.tagdoc;

//import org.apache.maven.doxia.sink.Sink;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeSet;

import javax.xml.namespace.QName;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import org.apache.commons.digester.AbstractObjectCreationFactory;
import org.apache.commons.digester.Digester;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.reporting.AbstractMavenMultiPageReport;
import org.apache.maven.reporting.MavenReportException;
import org.apache.maven.reporting.sink.SinkFactory;
import org.apache.myfaces.trinidadbuild.plugin.faces.parse.AbstractTagBean;
import org.apache.myfaces.trinidadbuild.plugin.faces.parse.ComponentBean;
import org.apache.myfaces.trinidadbuild.plugin.faces.parse.ConverterBean;
import org.apache.myfaces.trinidadbuild.plugin.faces.parse.EventBean;
import org.apache.myfaces.trinidadbuild.plugin.faces.parse.EventRefBean;
import org.apache.myfaces.trinidadbuild.plugin.faces.parse.ExampleBean;
import org.apache.myfaces.trinidadbuild.plugin.faces.parse.FacesConfigBean;
import org.apache.myfaces.trinidadbuild.plugin.faces.parse.FacesConfigParser;
import org.apache.myfaces.trinidadbuild.plugin.faces.parse.FacetBean;
import org.apache.myfaces.trinidadbuild.plugin.faces.parse.PropertyBean;
import org.apache.myfaces.trinidadbuild.plugin.faces.parse.ScreenshotBean;
import org.apache.myfaces.trinidadbuild.plugin.faces.parse.ValidatorBean;
import org.apache.myfaces.trinidadbuild.plugin.faces.util.ComponentFilter;
import org.apache.myfaces.trinidadbuild.plugin.faces.util.ConverterFilter;
import org.apache.myfaces.trinidadbuild.plugin.faces.util.FilteredIterator;
import org.apache.myfaces.trinidadbuild.plugin.faces.util.PropertyFilter;
import org.apache.myfaces.trinidadbuild.plugin.faces.util.ValidatorFilter;
import org.apache.myfaces.trinidadbuild.plugin.faces.util.XIncludeFilter;

import org.codehaus.doxia.sink.Sink;
import org.codehaus.doxia.site.renderer.SiteRenderer;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 * Report for generating JSF tagdoc based on faces-config.xml parsing.
 * Note that this is not really an AbstractMavenMultiPageReport - the
 * secondary pages are manually generated XDoc.  I tried using the true
 * multipage report approach, and ran into Maven bugs (as of Maven 2.0.2,
 * site 2.0b4).
 *
 * @goal tagdoc
 */
public class TagdocReport extends AbstractMavenMultiPageReport
{
  protected void executeReport( Locale locale )
      throws MavenReportException
  {
    // Why the heck doesn't Maven do this for me?
    SinkFactory factory = new SinkFactory();
    factory.setSiteRenderer(getSiteRenderer());
    factory.setSiteDirectory(getOutputDirectory());
    setSinkFactory(factory);

    processIndex(project, resourcePath);
    try
    {
      _generateTagDocs();
    }
    catch (Exception e)
    {
      throw new MavenReportException("Couldn't generate tagdoc", e);
    }
  }

  private void _generateTagDocs() throws Exception
  {
    FacesConfigBean facesConfig = getFacesConfig();
    if (!facesConfig.hasComponents())
    {
      getLog().info("Nothing to generate - no components found");
      return;
    }

    // Need to cycle through the components two times, hence need two iterators.
    // components Iterator will be used when actually writing out the tag doc
    // compIter Iterator will be used when creating the maps of component relationships
    Iterator<ComponentBean> components = facesConfig.components();
    components = new FilteredIterator(components, new SkipFilter());
    components = new FilteredIterator(components, new ComponentTagFilter());
    components = new FilteredIterator(components, new ComponentNamespaceFilter());

    Iterator<ComponentBean> compIter = facesConfig.components();
    compIter = new FilteredIterator(compIter, new SkipFilter());
    compIter = new FilteredIterator(compIter, new ComponentTagFilter());
    compIter = new FilteredIterator(compIter, new ComponentNamespaceFilter());

    // compTypeMap holds a map of compononent types to tag names that implement that component type
    // The map is built using getComponentType method on the component bean to determine the
    // component type of a given tag name
    Map<String, List<QName>>  compTypeMap = new HashMap<String, List<QName>> ();
    // contractMap holds a map of contract name to tag names that satisify that contract.
    // The map is built using the getSatisfiedContracts method API on the component bean to determine
    // which contracts are satisfied for a given tagname
    Map<String, List<QName>> contractMap = new HashMap<String, List<QName>>();

    while (compIter.hasNext())
    {
      ComponentBean compBean = compIter.next();
      List<QName> tagNames;
      String compType = compBean.getComponentType();
      if (compType != null &&
          compTypeMap.containsKey (compType) &&
          compBean.getTagName() != null)
      {
        // the component type map already contains an entry for this component type
        tagNames = compTypeMap.get(compType);
      }
      else
      {
        // the component type map does not contain an entry for this component type
        // so create a new ArrayList that will be used to store the tag names of
        // component that have this component type
        tagNames = new ArrayList<QName>();
      }
      tagNames.add(compBean.getTagName());
      compTypeMap.put (compType, tagNames);

      if (compBean.hasSatisfiedContracts())
      {
        Iterator<String> satContractsIter = compBean.satisfiedContracts();
        while (satContractsIter.hasNext())
        {
          String satContract = satContractsIter.next();
          if (contractMap.containsKey (satContract))
          {
            // the contract map already contains an entry for this contract
            tagNames = contractMap.get(satContract);
          }
          else
          {
            // the contract map does not contain an entry for this contract, so
            // create a new ArrayList which will be used to store the tag names of
            // components that satisfy this contract
            tagNames = new ArrayList<QName>();
          }
          tagNames.add(compBean.getTagName());
          contractMap.put (satContract, tagNames);
        }
      }
    }

    Iterator<ValidatorBean> validators = facesConfig.validators();
    validators = new FilteredIterator(validators, new ValidatorTagFilter());
    validators = new FilteredIterator(validators, new ValidatorNamespaceFilter());

    Iterator<ConverterBean> converters = facesConfig.converters();
    converters = new FilteredIterator(converters, new ConverterTagFilter());
    converters = new FilteredIterator(converters, new ConverterNamespaceFilter());

    // =-=AEW Note that only updating out-of-date components, etc. is
    // permanently tricky, even if we had proper detection in place,
    // because the index always has to have all docs
    /*
    if (!components.hasNext() && !converters.hasNext() && !validators.hasNext())
    {
      getLog().info("Nothing to generate - all docs are up to date");
      return;
    }
    */

    Set componentPages = new TreeSet();
    Set converterPages = new TreeSet();
    Set validatorPages = new TreeSet();

    int count = 0;
    while (components.hasNext())
    {
      String pageName = _generateComponentDoc(components.next(), compTypeMap, contractMap);
      if (pageName != null)
      {
        componentPages.add(pageName);
        count++;
      }
    }
    while (converters.hasNext())
    {
      String pageName = _generateConverterDoc(converters.next());
      if (pageName != null)
      {
        converterPages.add(pageName);
        count++;
      }
    }
    while (validators.hasNext())
    {
      String pageName = _generateValidatorDoc(validators.next());
      if (pageName != null)
      {
        validatorPages.add(pageName);
        count++;
      }
    }


    Set otherPages = _gatherOtherTags();

    getLog().info("Generated " + count + " page(s)");

    Sink sink = getSink();
    sink.head();
    sink.title();
    sink.text("Tag library documentation");
    sink.title_();
    sink.head_();
    sink.body();

    sink.sectionTitle1();
    sink.text("Tag library information");
    sink.sectionTitle1_();
    sink.section1();

    for (Iterator<Map.Entry> i = taglibs.entrySet().iterator(); i.hasNext(); )
    {
      Map.Entry entry = i.next();
      sink.paragraph();

      sink.bold();
      sink.text("Short name:");
      sink.bold_();
      sink.nonBreakingSpace();
      sink.text(entry.getKey().toString());
      sink.lineBreak();

      sink.bold();
      sink.text("Namespace:");
      sink.bold_();
      sink.nonBreakingSpace();
      sink.text(entry.getValue().toString());
      sink.lineBreak();

      sink.paragraph_();
    }

    sink.section1_();

    _writeIndexSection(sink, componentPages, "Components");
    _writeIndexSection(sink, converterPages, "Converters");
    _writeIndexSection(sink, validatorPages, "Validators");
    _writeIndexSection(sink, otherPages, "Miscellaneous");

    sink.body_();
  }

  private Set _gatherOtherTags()
  {
    TreeSet set = new TreeSet();
    String subDir =
      _platformAgnosticPath(_platformAgnosticPath("xdoc/" +
                                                  _DOC_SUBDIRECTORY));
    File siteSubDir = new File(siteDirectory, subDir);
    if (siteSubDir.exists())
    {
      String[] files = siteSubDir.list();
      for (int i = 0; i < files.length; i++)
      {
        String file = files[i];
        if (file.endsWith(".xml"))
        {
          set.add(file.substring(0, file.length() - 4));
        }
      }
    }

    return set;
  }

  private String _formatTagList(
    Iterator<String> strIter,
    Map <String, List<QName>> pMap,
    String header)
  {
    String formatted = null;

    // Don't know how long this will be, but 300 should be plenty.
    StringBuffer sb = new StringBuffer(300);
    sb.append("\n");

    // In the case of the component summary, the header is written out in a separate table cell, and so no
    // header text is passed into this method
    if (header != null && !header.isEmpty())
    {
      sb.append("<b>");
      sb.append(header);
      sb.append(":</b> ");
    }

    boolean gotOne = false;
    while (strIter.hasNext())
    {
      List<QName> tagNameList = pMap.get(strIter.next());
      if (tagNameList != null && !tagNameList.isEmpty())
      {
        Iterator<QName> tagNameIter  = tagNameList.iterator();

        while (tagNameIter.hasNext())
        {
          QName tagName = tagNameIter.next();

          if (gotOne)
          {
            sb.append(", ");
          }
          String tagdocURL = _platformAgnosticPath("../tagdoc/" +
            _toPageName(tagName) + ".html");
          sb.append("<a href=\"" + tagdocURL + "\">");
          sb.append(_getQualifiedName(tagName));
          sb.append("</a>");
          gotOne = true;
        }
      }
    }
    if (gotOne)
    {
      sb.append("<br/>\n");
      formatted = sb.toString();
    }
    return formatted;
  }

  private String _formatPropList(
    String[] pList,
    String   header)
  {
    String[] nullList = {};
    return _formatPropList(pList, header, nullList);
  }

  private String _formatPropList(
    String[] pList,
    String   header,
    String[] ignores)
  {
    String formatted = null;
    if ((pList != null) && (pList.length > 0))
    {
      // Don't know how long this will be, but 100 should be plenty.
      StringBuffer sb = new StringBuffer(100);
      sb.append("\n");

      // In the case of the component summary section the header text is written out
      // in a separate table cell, so no header text is passed into this method
      if (header != null && !header.isEmpty())
      {
        sb.append("<b>");
        sb.append(header);
        sb.append(":</b> ");
      }

      boolean gotOne = false;

      for (int arrInd = 0; arrInd < pList.length; arrInd++)
      {
        String curStr = pList[arrInd];
        outer:
        if (curStr != null)
        {
          for (int i = 0; i < ignores.length; i++)
          {
            String s = ignores[i];
            if ((s != null) && (s.equalsIgnoreCase(curStr)))
              break outer;
          }

          if (gotOne)
          {
            sb.append(", ");
          }
          gotOne = true;
          sb.append(curStr);
        }
      }
      if (gotOne)
      {
        sb.append("<br/>\n");
        formatted = sb.toString();
      }
    }
    return formatted;
  }

  private void _writeIndexSection(Sink sink, Set pages, String title)
  {
    if (pages.isEmpty())
      return;

    sink.sectionTitle1();
    sink.text(title);
    sink.sectionTitle1_();
    sink.section1();
    sink.table();
    sink.tableRow();
    sink.tableHeaderCell();
    sink.text("Tag Name");
    sink.tableHeaderCell_();
    sink.tableRow_();

    Iterator<String> iter = pages.iterator();
    while (iter.hasNext())
    {
      sink.tableRow();
      sink.tableCell();

      String name = iter.next();
      String tagName = "<" + name.replace('_', ':') + ">";

      sink.link(_DOC_SUBDIRECTORY + "/" + name + ".html");
      sink.text(tagName);
      sink.link_();

      sink.tableCell_();
      sink.tableRow_();
    }

    sink.table_();
    sink.section1_();
  }

  public boolean usePageLinkBar()
  {
    return false;
  }

  private String _toPageName(QName qName)
  {
    return _getPrefix(qName) + "_" + qName.getLocalPart();
  }

  private String _getQualifiedName(QName qName)
  {
    return _getPrefix(qName) + ":" + qName.getLocalPart();
  }

  private String _getPrefix(QName qName)
  {
    if ((qName.getPrefix() != null) && !"".equals(qName.getPrefix()))
      return qName.getPrefix();

    String namespace = qName.getNamespaceURI();
    if (namespace == null)
      return null;

    for (Iterator<Map.Entry> i = taglibs.entrySet().iterator(); i.hasNext(); )
    {
      Map.Entry entry = i.next();
      if (namespace.equals(entry.getValue()))
        return (String) entry.getKey();
    }

    return "unknown";
  }

  private String _generateComponentDoc(ComponentBean component, Map<String, List<QName>> compTypeMap, Map <String, List<QName>> contractMap)
    throws Exception
  {
    if (component.getTagName() == null)
    {
      return null;
    }
    String pageName = _toPageName(component.getTagName());

    File targetDir = new File(outputDirectory.getParentFile(),
                              _platformAgnosticPath("generated-site/xdoc/" +
                                                      _DOC_SUBDIRECTORY));
    targetDir.mkdirs();
    File targetFile = new File(targetDir, pageName + ".xml");

    Writer out = new OutputStreamWriter(new FileOutputStream(targetFile),
                                        "UTF-8");
    try
    {
      out.write("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n");
      out.write("<document>\n");
      out.write(" <properties>\n");
      out.write("  <title>&lt;" + _getQualifiedName(component.getTagName()) + "&gt;</title>\n");
      out.write(" </properties>\n");
      out.write(" <body>\n");

      out.write(" <section name=\"Summary\">\n");
      out.write(" <p>\n");
      _writeComponentSummary(out, component, contractMap);
      out.write(" </p>\n");
      out.write(" </section>\n");

      _writeScreenshots(out, component);

      _writeExamples(out, component);

      _writeAccessibilityGuidelines(out, component);

      if (component.isClientBehaviorHolder())
      {
        out.write(" <section name=\"Supported Client Events for Client Behaviors\">\n");
        out.write(" <p>\n");
        _writeComponentClientEvents(out, component);
        out.write(" </p>\n");
        out.write(" </section>\n");
      }

      if (component.hasEvents(true))
      {
        out.write(" <section name=\"Events\">\n");
        out.write(" <p>\n");
        _writeComponentEvents(out, component);
        out.write(" </p>\n");
        out.write(" </section>\n");
      }

      if (component.hasFacets(true))
      {
        out.write(" <section name=\"Supported Facets\">\n");
        out.write(" <p>\n");
        _writeComponentFacets(out, component, compTypeMap);
        out.write(" </p>\n");
        out.write(" </section>\n");
      }

      out.write(" <section name=\"Attributes\">\n");
      _writeComponentAttributes(out, component);
      out.write(" </section>\n");

      out.write(" </body>\n");
      out.write("</document>\n");
    }
    finally
    {
      out.close();
    }

    return pageName;
  }

  private String _generateConverterDoc(ConverterBean converter) throws IOException
  {
    if (converter.getTagName() == null)
    {
      return null;
    }

    String pageName = _toPageName(converter.getTagName());

    File targetDir = new File(outputDirectory.getParentFile(),
                              _platformAgnosticPath("generated-site/xdoc/" +
                                                     _DOC_SUBDIRECTORY));
    targetDir.mkdirs();
    File targetFile = new File(targetDir, pageName + ".xml");

    Writer out = new OutputStreamWriter(new FileOutputStream(targetFile),
                                        "UTF-8");
    try
    {
      out.write("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n");
      out.write("<document>\n");
      out.write(" <properties>\n");
      out.write("  <title>&lt;" + _getQualifiedName(converter.getTagName()) + "&gt;</title>\n");
      out.write(" </properties>\n");
      out.write(" <body>\n");

      out.write(" <section name=\"Summary\">\n");
      out.write(" <p>\n");
      _writeConverterSummary(out, converter);
      out.write(" </p>\n");
      out.write(" </section>\n");

      _writeScreenshots(out, converter);

      _writeExamples(out, converter);

      out.write(" <section name=\"Attributes\">\n");
      _writeConverterAttributes(out, converter);
      out.write(" </section>\n");

      out.write(" </body>\n");
      out.write("</document>\n");
    }
    finally
    {
      out.close();
    }

    return pageName;
  }

  private String _generateValidatorDoc(ValidatorBean validator) throws IOException
  {
    if (validator.getTagName() == null)
    {
      return null;
    }

    String pageName = _toPageName(validator.getTagName());

    File targetDir = new File(outputDirectory.getParentFile(),
                              _platformAgnosticPath("generated-site/xdoc/" +
                                                      _DOC_SUBDIRECTORY));
    targetDir.mkdirs();
    File targetFile = new File(targetDir, pageName + ".xml");

    Writer out = new OutputStreamWriter(new FileOutputStream(targetFile),
                                        "UTF-8");
    try
    {
      out.write("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n");
      out.write("<document>\n");
      out.write(" <properties>\n");
      out.write("  <title>&lt;" + _getQualifiedName(validator.getTagName()) + "&gt;</title>\n");
      out.write(" </properties>\n");
      out.write(" <body>\n");

      out.write(" <section name=\"Summary\">\n");
      out.write(" <p>\n");
      _writeValidatorSummary(out, validator);
      out.write(" </p>\n");
      out.write(" </section>\n");

      _writeScreenshots(out, validator);

      _writeExamples(out, validator);

      out.write(" <section name=\"Attributes\">\n");
      _writeValidatorAttributes(out, validator);
      out.write(" </section>\n");

      out.write(" </body>\n");
      out.write("</document>\n");
    }
    finally
    {
      out.close();
    }

    return pageName;
  }


  private void _writeComponentSummary(Writer out, ComponentBean bean, Map <String, List<QName>> contractMap) throws IOException
  {
    // In order to align all the Summary parts, create an HTML table.  The first column will be in bold, the second column in normal font.
    // The rows of the table are:
    // 1. Tag name
    // 2. Java class
    // 3. JavaScript class (optional)
    // 4. Component type
    // 5. Required ancestors (optional)
    // 6. Naming container (optional)
    // 7. Unsupported agents (optional)

    out.write("<div class=\'summary\'>\n");
    out.write("<table>\n");
    out.write("<tr>\n");
    out.write("<td><b>Tag Name:</b></td>");
    out.write("<td>&lt;" + _getQualifiedName(bean.getTagName()) + "&gt;</td>\n");
    out.write("</tr>\n");

    out.write("<tr>\n");
    out.write("<td><b>Java Class:</b></td>");
    String javadocURL = _platformAgnosticPath("../apidocs/" +
      bean.getComponentClass().replace('.', '/') + ".html");
    out.write("<td><a href=\"" + javadocURL + "\">");
    out.write(bean.getComponentClass());
    out.write("</a></td>\n");
    out.write("</tr>\n");

    // Write out the corresponding Java Script class for this component with a link to its JavaScript doc
    String jsClass = bean.getJsComponentClass();
    if (jsClass != null && !jsClass.isEmpty())
    {
      out.write("<tr>\n");
      out.write("<td><b>JavaScript Class:</b></td>");
      String jsdocURL = _platformAgnosticPath("../js_docs_out/" + jsClass.replace('.', '/') + ".html");
      out.write("<td><a href=\"" + jsdocURL + "\">");
      out.write(jsClass);
      out.write("</a></td>\n");
      out.write("</tr>\n");
    }

    out.write("<tr>\n");
    out.write("<td><b>Component Type:</b></td>");
    out.write("<td>" + bean.getComponentType() +  "</td>\n");
    out.write("</tr>\n");

    if (bean.hasRequiredAncestorContracts())
    {
      String formattedAncestors = _formatTagList ( bean.requiredAncestorContracts(),
                                                   contractMap,
                                                   null);
      out.write("<tr>\n");
      out.write("<td><b>Required Ancestor Tag(s):</b></td>");
      out.write("<td>" + formattedAncestors + "</td>");
      out.write("</tr>\n");
    }

    if (_isNamingContainer(bean))
    {
      out.write("<tr>\n");
      out.write("<td><b>Naming Container:</b></td>");
      out.write("<td>Yes.  When referring to children of this " +
                "component (\"partialTriggers\", <code>findComponent()</code>, etc.), " +
                "you must prefix the child's ID with this component's ID and a colon (':').</td>");
      out.write("</tr>\n");
    }

    String fmtd = _formatPropList(bean.getUnsupportedAgents(),
                                  null,
                                  _NON_DOCUMENTED_AGENTS);
    if (fmtd != null)
    {
      out.write("<tr>\n");
      out.write("<td><b>Unsupported Agent(s):</b></td>");
      out.write("<td>" + fmtd + "</td>");
      out.write("</tr>\n");
    }

    out.write("</table>\n");
    out.write("</div>\n");
    
    String deprecatedMessage = bean.getDeprecated();
    if (deprecatedMessage != null)
    {
      out.write("\n");
      out.write("<b>DEPRECATED: </b>");
      out.write(_preToSource(deprecatedMessage));
      out.write("\n");
    }

    String doc = bean.getLongDescription();
    if (doc == null)
      doc = bean.getDescription();

    out.write(_preToSource(doc));
    out.write("\n");
  }


  private boolean _isNamingContainer(ComponentBean bean)
  {
    if (bean.isNamingContainer())
      return true;

    ComponentBean parent = bean.resolveSupertype();
    if (parent == null)
      return false;
    return _isNamingContainer(parent);
  }

  private void _writeValidatorSummary(Writer out, ValidatorBean bean) throws IOException
  {
    out.write("<div class=\'summary\'>\n");
    out.write("<table>\n");

    out.write("<tr>\n");
    out.write("<td><b>Tag Name:</b></td>");
    out.write("<td>" + _getQualifiedName(bean.getTagName()) + "&gt;</td>\n");
    out.write("</tr>\n");

    out.write("<tr>\n");
    out.write("<td><b>Type:</b></td>");
    out.write("<td>" + bean.getValidatorId() +  "</td>\n");
    out.write("</tr>\n");
    out.write("</table>\n");
    out.write("</div>\n");

    String doc = bean.getLongDescription();
    if (doc == null)
      doc = bean.getDescription();

    out.write(_preToSource(doc));
    out.write("\n");
  }


  private void _writeConverterSummary(Writer out, ConverterBean bean) throws IOException
  {
    out.write("<div class=\'summary\'>\n");
    out.write("<table>\n");

    out.write("<tr>\n");
    out.write("<td><b>Tag Name:</b></td>");
    out.write("<td>" + _getQualifiedName(bean.getTagName()) + "&gt;</td>\n");
    out.write("</tr>\n");

    out.write("<tr>\n");
    out.write("<td><b>Type:</b></td>");
    out.write("<td>" + bean.getConverterId() +  "</td>\n");
    out.write("</tr>\n");
    out.write("</table>\n");
    out.write("</div>\n");

    String doc = bean.getLongDescription();
    if (doc == null)
      doc = bean.getDescription();

    out.write(_preToSource(doc));
    out.write("\n");
  }

  static private final String _preToSource(String in)
  {
    in = in.replaceAll("<pre>", "<source><![CDATA[");
    in = in.replaceAll("</pre>", "]]></source>");
    in = in.replaceAll("<html:", "<");
    in = in.replaceAll("</html:", "</");

    return in;
  }

  static private final String _platformAgnosticPath(String path) {
      return path.replace('/', File.separatorChar);
  }


  private class GroupComparator implements Comparator
  {
    public int compare(Object o1, Object o2)
    {
      return _getGroupIndex(o1) - _getGroupIndex(o2);
    }

    public boolean equals(Object o)
    {
      return (o instanceof GroupComparator);
    }

    private int _getGroupIndex(Object o)
    {
      String s = (o == null) ? null : o.toString();




      if ("message".equalsIgnoreCase(s))
      {
        return 0;
      }

      if ("core".equalsIgnoreCase(s))
      {
        return 1;
      }
      if ("events".equalsIgnoreCase(s))
      {
        return 2;
      }

      if (s != null)
        getLog().warn("UNKNOWN ATTRIBUTE GROUP: " + s);

      return 3;
    }
  }


  private void _writeComponentAttributes(Writer out, ComponentBean bean) throws IOException
  {
    // Sort the names
    TreeSet<String> attributes = new TreeSet<String>();
    Iterator<PropertyBean> attrs = bean.properties(true);
    attrs = new FilteredIterator(attrs, new NonHiddenFilter());

    while (attrs.hasNext())
    {
      PropertyBean property = attrs.next();
      if (!property.isTagAttributeExcluded())
        attributes.add(property.getPropertyName());
    }

    // Now get a list of PropertyBeans
    List<PropertyBean> list = new ArrayList<PropertyBean>();
    Iterator<String> iter = attributes.iterator();
    while (iter.hasNext())
    {
      String attrName = iter.next();
      list.add(bean.findProperty(attrName, true));
    }

    TreeSet<String> groups = new TreeSet<String>(new GroupComparator());
    /* No current support for grouping
    // Make sure "null" is the representative for unknown groups
    Iterator iter = attributes.iterator();
    while (iter.hasNext())
    {
      String group = ((AttributeDoc) iter.next()).group;
      if (group != null)
        groups.add(group);
    }
    */

    _writeComponentAttributes(out,
                              list.iterator(),
                              bean.getComponentClass(),
                              groups.isEmpty() ? null : "Ungrouped");

    Iterator<String> groupIter = groups.iterator();
    while (groupIter.hasNext())
    {
      _writeComponentAttributes(out,
                                list.iterator(),
                                bean.getComponentClass(),
                                groupIter.next());
    }
  }

  private void _writeConverterAttributes(Writer out, ConverterBean bean) throws IOException
  {
    // Sort the names
    TreeSet attributes = new TreeSet();
    Iterator<PropertyBean> attrs = bean.properties();
    while (attrs.hasNext())
    {
      PropertyBean property = attrs.next();
      if (!property.isTagAttributeExcluded())
        attributes.add(property.getPropertyName());
    }

    // Now get a list of PropertyBeans
    List list = new ArrayList();
    Iterator<String> iter = attributes.iterator();
    while (iter.hasNext())
    {
      String attrName = iter.next();
      list.add(bean.findProperty(attrName));
    }

    _writeComponentAttributes(out,
                              list.iterator(),
                              bean.getConverterClass(),
                              null);
  }



  private void _writeValidatorAttributes(Writer out, ValidatorBean bean) throws IOException
  {
    // Sort the names
    TreeSet attributes = new TreeSet();
    Iterator<PropertyBean> attrs = bean.properties();
    while (attrs.hasNext())
    {
      PropertyBean property = attrs.next();
      if (!property.isTagAttributeExcluded())
        attributes.add(property.getPropertyName());
    }

    // Now get a list of PropertyBeans
    List list = new ArrayList();
    Iterator<String> iter = attributes.iterator();
    while (iter.hasNext())
    {
      String attrName = iter.next();
      list.add(bean.findProperty(attrName));
    }

    _writeComponentAttributes(out,
                              list.iterator(),
                              bean.getValidatorClass(),
                              null);
  }



  private void _writeComponentAttributes(Writer out,
                                         Iterator<PropertyBean> attributes,
                                         String className,
                                         String group) throws IOException
  {
    boolean writtenAnyAttributes = false;

    while (attributes.hasNext())
    {
      PropertyBean attr = attributes.next();

      /*
      if ((group == null) || "Ungrouped".equals(group))
      {
        if (doc.group != null)
          continue;
      }
      else
      {
        if (!group.equalsIgnoreCase(doc.group))
          continue;
      }
      */

      if (!writtenAnyAttributes)
      {
        writtenAnyAttributes = true;
        if (group != null)
        {
          String sectionName;
          if ("events".equalsIgnoreCase(group))
            sectionName = "Javascript attributes";
          else
            sectionName = group + " attributes";

          out.write("<subsection name=\"" + sectionName + "\">\n");
        }

        out.write("<table>\n");
        out.write("<tr>\n");
        out.write("<th>Name</th>\n");
        out.write("<th>Type</th>\n");
        out.write("<th>Supports EL?</th>\n");
        if (!_attrDocSpansColumns)
          out.write("<th>Description</th>\n");
        out.write("</tr>\n");
      }

      String propertyName = attr.getPropertyName();
      // Quick fix of problems with actionExpression vs. action
      // actionExpression is the MethodExpression on the component,
      // but on the tag it's 'action'
      if ("actionExpression".equals(propertyName))
        propertyName = "action";

      out.write("<tr>\n");
      out.write("<td>" + propertyName + "</td>");
      String type = _getDisplayType(className,
                                    propertyName,
                                    attr.getPropertyClass());

      out.write("<td>" + type + "</td>");

      String elSupported;
      // MethodBindings, "binding", and some other attributes
      // require EL support
      if (attr.isMethodBinding() ||
          attr.isMethodExpression() ||
          "binding".equals(propertyName))
      {
        // "action" doesn't require EL; all else do.
        elSupported = "action".equals(propertyName) ? "Yes" : "Only EL";
      }
      else
      {
        elSupported = attr.isLiteralOnly() ? "No" : "Yes";
      }

      out.write("<td>" + elSupported + "</td>");

      if (attr.getDescription()  != null)
      {
        String valStr = _formatPropList(attr.getPropertyValues(),
                                        "Valid Values");

        // The default value for the attribute. defaultValueStr will be null if no
        // default value is specified via <default-value> in component xml file.
        // Since _formatPropList takes an array as the first input param, covert the default
        // value into a single item array when calling formatPropList
        String defaultValueStr = _formatPropList (new String[] { attr.getDefaultValue() },
                                        "Default Value");

        String unsupAgentsStr =
          _formatPropList(attr.getUnsupportedAgents(),
                          "Not supported on the following agents",
                          _NON_DOCUMENTED_AGENTS);
        String unsupRkStr =
          _formatPropList(attr.getUnsupportedRenderKits(),
                          "Not supported on the following renderkits");

        if (_attrDocSpansColumns)
        {
          out.write("</tr>\n");
          out.write("<tr>\n");
          out.write("<td colspan=\"3\">\n");
        }
        else
        {
          out.write("<td>\n");
        }

        //        out.write(EscapeUtils.escapeElementValue(doc.doc));
        if (valStr != null)
        {
          out.write(valStr);
        }

        if (defaultValueStr != null)
        {
          out.write(defaultValueStr);
        }

        // if we print out a list of possible values and/or a default value for the attribute,
        // then enter a line break before printing out other information about the attribute.
        if (valStr != null || defaultValueStr != null)
        {
          out.write("<br/>");
        }

        if (attr.getDeprecated() != null)
        {
          out.write("<b>");
          out.write(attr.getDeprecated());
          out.write("</b>");
        }

        if (attr.isNoOp())
        {
          out.write("<b>");
          out.write("This property has a no-op setter for both the client and server components effectively making it a read-only property.");
          out.write("</b>");
        }

        if (attr.isNoOp() || attr.getDeprecated() != null)
        {
          out.write("<br/><br/>");
        }

        out.write(attr.getDescription());
        if (unsupAgentsStr != null)
        {
          out.write("<br/>");
          out.write(unsupAgentsStr);
        }
        if (unsupRkStr != null)
        {
          out.write("<br/>");
          out.write(unsupRkStr);
        }
        //out.write(EscapeUtils.escapeAmpersands(doc.doc));
        out.write("</td>\n");
      }

      out.write("</tr>\n");
    }

    if (writtenAnyAttributes)
    {
      out.write("</table>\n");
      if (group != null)
        out.write("</subsection>\n");
    }
  }

  static private String _getDisplayType(String className, String name, String type)
  {
    if (type.startsWith("java.lang."))
    {
      return type.substring("java.lang.".length());
    }
    else if ("binding".equals(name))
    {
      StringTokenizer tokens = new StringTokenizer(className, ".", true);
      String out = "";
      while (tokens.hasMoreTokens())
      {
        String token = tokens.nextToken();
        out = out + token;
        // Give ourselves an opportunity for a line break after "component.";
        if (out.endsWith("component."))
          out = out + "<wbr/>";
      }

      return out;
    }

    return type;
  }

  private void _writeComponentClientEvents(
    Writer        out,
    ComponentBean bean
    ) throws IOException
  {
    String defaultEvent = bean.getDefaultEventName();

    out.write("<table>\n<tbody>\n<tr>\n<td>\n<ul>\n");

    String[] eventNames = bean.getEventNames();
    int size = eventNames.length;
    String[] dest = new String[size];
    System.arraycopy(eventNames, 0, dest, 0, size);
    Arrays.sort(dest);
    // create 3 columns to better utilize the space on the page
    int numRows = (int)Math.ceil(size / 3d);

    for (int i = 0; i < size; ++i)
    {
      if (i > 0 && (i % numRows) == 0)
      {
        out.write("</ul>\n</td>\n<td>\n<ul>\n");
      }
      String eventName = dest[i];
      out.write("<li>");
      out.write(eventName);
      if (eventName.equals(defaultEvent))
      {
        out.write(" <small>(default)</small>");
      }
      out.write("</li>\n");
    }
    out.write("</ul>\n</td>\n</tr>\n</tbody>\n</table>\n");
  }

  private void _writeComponentEvents(Writer out, ComponentBean bean) throws IOException
  {
    out.write("<table>\n");
    out.write("<tr>\n");
    out.write("<th>Type</th>\n");
    out.write("<th>Phases</th>\n");
    out.write("<th>Description</th>\n");
    out.write("</tr>\n");

    Iterator<EventRefBean> iter = bean.events(true);
    while (iter.hasNext())
    {
      EventRefBean eventRef = iter.next();
      EventBean    event = eventRef.resolveEventType();

      out.write("<tr>\n");
      out.write("<td>" + event.getEventClass() + "</td>");
      out.write("<td nowrap=\"nowrap\">");
      String[] phases = eventRef.getEventDeliveryPhases();
      for (int i = 0; i < phases.length; i++)
      {
        if (i > 0)
          out.write(",<br/>");
        out.write(phases[i]);
      }

      out.write("</td>");
      out.write("<td>" + event.getDescription() + "</td>");
      out.write("</tr>\n");
    }

    out.write("</table>\n");
  }


  private void _writeComponentFacets(Writer out, ComponentBean bean, Map<String, List<QName>> compTypeMap) throws IOException
  {
    // Sort the facets
    TreeSet facetNames = new TreeSet();
    Iterator<FacetBean> iter = bean.facets(true);
    while (iter.hasNext())
    {
      FacetBean facetBean = iter.next();
      if (!facetBean.isHidden())
      {
        facetNames.add(facetBean.getFacetName());
      }
    }

    out.write("<table>\n");
    out.write("<tr>\n");
    out.write("<th>Name</th>\n");
    out.write("<th>Description</th>\n");
    out.write("</tr>\n");

    Iterator<String> nameIter = facetNames.iterator();
    while (nameIter.hasNext())
    {
      String name = nameIter.next();
      FacetBean facet = bean.findFacet(name, true);
      out.write("<tr>\n");
      out.write("<td>" + facet.getFacetName() + "</td>");
      out.write("<td>");

      if (facet.hasAllowedChildComponents())
      {
        String formattedChildComps = _formatTagList (facet.allowedChildComponents(), compTypeMap, "Allowed Child Components");
        if (formattedChildComps != null)
        {
          out.write(formattedChildComps);
          out.write("<br/>");
        }
      }

      out.write(facet.getDescription());
      out.write("</td>\n");
      out.write("</tr>\n");
    }

    out.write("</table>\n");
  }

  private void _writeExamples(Writer out, AbstractTagBean bean) throws IOException
  {
    if (!bean.hasExamples())
      return;

    ExampleBean exBean = null;

    // Write header
    out.write(" <section name=\"Code Example(s)\">\n");
    out.write(" <p>\n");
    out.write("   <html>\n");

    // Go through each example, write its description
    // followed by the example source code.
    Iterator<ExampleBean> iter = bean.examples();
    while (iter.hasNext())
    {
      exBean = iter.next();
      String desc   = exBean.getSourceDescription();
      String source = exBean.getSourceCode();

      if (desc != null)
      {
        desc = desc.replaceAll("<", "&lt;");
        desc = desc.replaceAll(">", "&gt;");

        if (!"".equals(desc))
          out.write("   <p>" + desc + "</p>");
      }

      if (source != null)
      {
        source = source.replaceAll("<", "&lt;");
        source = source.replaceAll(">", "&gt;");
        if (!"".equals(source))
        {
          out.write("    <div class=\'source\'>\n");
          out.write("      <pre>\n" + source + "</pre>\n");
          out.write("    </div>\n");
        }
      }
    }
    out.write("   </html>\n");
    out.write(" </p>\n");
    out.write(" </section>\n");
  }

  private void _writeScreenshots(Writer out, AbstractTagBean bean) throws IOException
  {
    if (!bean.hasScreenshots())
      return;

    ScreenshotBean ssBean = null;

    // Write header
    out.write(" <section name=\"Screenshot(s)\">\n");
    out.write(" <p>\n");
    out.write("   <html>\n");

    // Go through each screenshot, write its image
    // followed by the image's caption.
    Iterator<ScreenshotBean> iter = bean.screenshots();
    while (iter.hasNext())
    {
      ssBean = iter.next();
      String desc   = ssBean.getDescription();
      String img = ssBean.getImage();

      out.write("    <div class=\'screenshot\'>\n");

      if (img != null)
      {
        if (!"".equals(img))
        {
          out.write(img);
        }
      }

      if (desc != null)
      {
        desc = desc.replaceAll("<", "&lt;");
        desc = desc.replaceAll(">", "&gt;");

        if (!"".equals(desc))
        {
          out.write("<br/>");
          out.write(desc + "\n");
        }
      }
      out.write("    </div>\n");

      // create extra space between each screenshot to ensure it is clear which description
      // text belongs to which image
      if (iter.hasNext())
      {
        out.write("<br/>");
      }
    }
    out.write("   </html>\n");
    out.write(" </p>\n");
    out.write(" </section>\n");
  }

  // Write out the accessibility Guidelines for the component.  Accessibility Guidelines
  // help the application developer to create an application that can be used by users that e.g.
  // use a screen reader (e.g JAWS).  Oftentimes, in order to be accessibility compliant (e.g. section 508
  // compliant) an application developer needs to specify metadata for the screenreader application
  // to be able to correctly interpret the application for the blind user.  The accessibility guideline
  // can be associated with the Component itself, with a specific attribute of the component
  // or with a specific facet of the component.  All accessibility guidelines are printed out
  // together in an "Accessibility Guidelines" section, with the component-generic guidelines
  // listed first, followed by the attribute specific guidelines and finally the facet-specific
  // guidelines
  private void _writeAccessibilityGuidelines(Writer out, ComponentBean bean) throws IOException
  {
    // accAttributes and accFacets are sorted lists of attributes and facets, respectively,
    // that have an associated accessibility guideline
    TreeSet<PropertyBean> accAttributes = new TreeSet<PropertyBean>();
    TreeSet<String> accFacets = new TreeSet<String>();

    // see if any of the component's properties has an associated accessibility guideline
    Iterator<PropertyBean> attrs = bean.properties();
    while (attrs.hasNext())
    {
      PropertyBean property = attrs.next();
      if (!property.isTagAttributeExcluded() && property.hasAccessibilityGuidelines())
      {
        accAttributes.add(property);
      }
    }

    // see if any of the component's facets has an associated accessibility guideline
    if (bean.hasFacets())
    {
      Iterator<FacetBean> facets = bean.facets(true);
      while (facets.hasNext())
      {
        FacetBean facetBean = facets.next();
        if (!facetBean.isHidden() && facetBean.hasAccessibilityGuidelines())
        {
          accFacets.add(facetBean.getFacetName());
        }
      }
    }

    // if neither the component nor the component's attributes nor the component's facets
    // has an accessibility guideline, return
    if (!bean.hasAccessibilityGuidelines() && accAttributes.isEmpty() && accFacets.isEmpty())
      return;

    String accGuideline;

    // Write header
    out.write(" <section name=\"Accessibility Guideline(s)\">\n");
    out.write(" <p>\n");
    out.write("   <html>\n");
    out.write("     <ul>");

    // write out component-generic accessibility guidelines, i.e. accessibility
    // guidelines that apply to the component as a whole, not associated with a
    // specific attribute
    if (bean.hasAccessibilityGuidelines())
    {
      Iterator<String> iter = bean.accessibilityGuidelines();
      while (iter.hasNext())
      {
        accGuideline = iter.next();
        _writeAccessibilityGuideline(out, "", accGuideline);
      }
    }

    // Write out attribute-specific accessibility guidelines.  Each attribute can have
    // one or more associated accessibility guidelines.
    if (!accAttributes.isEmpty())
    {
      Iterator<PropertyBean> propIter = accAttributes.iterator();
      while (propIter.hasNext())
      {
        PropertyBean property = propIter.next();
        Iterator<String> propAccIter = property.accessibilityGuidelines();
        while (propAccIter.hasNext())
        {
          accGuideline = propAccIter.next();
          _writeAccessibilityGuideline(out, property.getPropertyName() + " attribute", accGuideline);
        }
      }
    }

    // Write out facet-specific accessibility guidelines. A facet in the accFacets iterator
    // can have one or more associated accessibility guidelines
    if (!accFacets.isEmpty())
    {
      Iterator<String> facetIter = accFacets.iterator();
      while (facetIter.hasNext())
      {
        String facetName = facetIter.next();
        FacetBean facet = bean.findFacet(facetName, true);

        Iterator<String> facetAccIter = facet.accessibilityGuidelines();
        while (facetAccIter.hasNext())
        {
          accGuideline = facetAccIter.next();
          _writeAccessibilityGuideline(out, facetName + " facet", accGuideline);
        }
      }
    }

    out.write("     </ul>");
    out.write("   </html>\n");
    out.write(" </p>\n");
    out.write(" </section>\n");
  }

  // Write out an Accessibility Guideline
  // A bullet in an unordered list, followed by (optionally) the reference name in bold, e.g. the
  // name of the attribute or name of the facet which the guideline applies to, then the text
  // of the accessibility guideline.  For accessibility guidelines on the component, the referenceName
  // attribute is left blank.
  private void _writeAccessibilityGuideline(Writer out, String referenceName, String desc) throws IOException
  {
    out.write("    <div class=\'accGuideline\'>\n");
    out.write("<li>");

    if (!"".equals(referenceName))
    {
      out.write("<b>");
      out.write(referenceName);
      out.write("</b>: ");
    }

    if (desc != null)
    {
      if (!"".equals(desc))
      {
        out.write(desc + "\n");
      }
    }

    out.write("</li>");
    out.write("    </div>\n");
  }

  protected MavenProject getProject()
  {
    return project;
  }

  protected String getOutputDirectory()
  {
    return outputDirectory.getAbsolutePath();
  }

  protected SiteRenderer getSiteRenderer()
  {
    return siteRenderer;
  }

  public String getName( Locale locale )
  {
    return "JSF Tag Documentation";
  }

  public String getDescription( Locale locale )
  {
    return "Documentation for JSF Tags";
  }

  public String getOutputName()
  {
    return "tagdoc";
  }

  protected void processIndex(
    MavenProject project,
    String       resourcePath) throws MavenReportException
  {
    _facesConfig = new FacesConfigBean();

    URL[] index = readIndex(project);
    for (int i=0; i < index.length; i++)
    {
      processIndexEntry(index[i]);
    }
  }

  protected void processIndexEntry(
    URL entry) throws MavenReportException
  {
    try
    {
      new FacesConfigParser().merge(_facesConfig, entry);
    }
    catch (MojoExecutionException e)
    {
      throw new MavenReportException("Couldn't parse faces config",e);
    }
  }

  protected FacesConfigBean getFacesConfig()
  {
    return _facesConfig;
  }


  protected List getMasterConfigs(
    MavenProject project) throws MavenReportException
  {
    String resourcePath = "META-INF/maven-faces-plugin/faces-config.xml";
    return getCompileDependencyResources(project, resourcePath);
  }

  protected List getCompileDependencyResources(
    MavenProject project,
    String       resourcePath) throws MavenReportException
  {
    try
    {
      ClassLoader cl = createCompileClassLoader(project);
      Enumeration e = cl.getResources(resourcePath);
      List urls = new ArrayList();
      while (e.hasMoreElements())
      {
        URL url = (URL)e.nextElement();
        urls.add(url);
      }
      return Collections.unmodifiableList(urls);
    }
    catch (IOException e)
    {
      throw new MavenReportException("Unable to get resources for path " +
                                       "\"" + resourcePath + "\"", e);
    }

  }

  protected URL[] readIndex(
    MavenProject project) throws MavenReportException
  {
    try
    {
      // 1. read master faces-config.xml resources
      List masters = getMasterConfigs(project);
      if (masters.isEmpty())
      {
        getLog().warn("Master faces-config.xml not found");
        return new URL[0];
      }
      else
      {
        List entries = new LinkedList();

        SAXParserFactory spf = SAXParserFactory.newInstance();
        spf.setNamespaceAware(true);
        // requires JAXP 1.3, in JavaSE 5.0
        // spf.setXIncludeAware(false);

        for (Iterator<URL> i=masters.iterator(); i.hasNext();)
        {
          URL url = i.next();
          Digester digester = new Digester(spf.newSAXParser());
          digester.setNamespaceAware(true);

          // XInclude
          digester.setRuleNamespaceURI(XIncludeFilter.XINCLUDE_NAMESPACE);
          digester.addCallMethod("faces-config/include", "add", 1);
          digester.addFactoryCreate("faces-config/include",
                                    URLCreationFactory.class);
          digester.addCallParam("faces-config/include", 0, 0);

          digester.push(url);
          digester.push(entries);
          digester.parse(url.openStream());
        }

        return (URL[])entries.toArray(new URL[0]);
      }
    }
    catch (ParserConfigurationException e)
    {
      throw new MavenReportException("Failed to parse master config", e);
    }
    catch (SAXException e)
    {
      throw new MavenReportException("Failed to parse master config", e);
    }
    catch (IOException e)
    {
      throw new MavenReportException("Failed to parse master config", e);
    }
  }

  private ClassLoader createCompileClassLoader(
    MavenProject project) throws MavenReportException
  {
    Thread current = Thread.currentThread();
    ClassLoader cl = current.getContextClassLoader();

    try
    {
      List classpathElements = project.getCompileClasspathElements();
      if (!classpathElements.isEmpty())
      {
        String[] entries = (String[]) classpathElements.toArray(new String[0]);
        URL[] urls = new URL[entries.length];
        for (int i=0; i < urls.length; i++)
        {
          urls[i] = new File(entries[i]).toURL();
        }
        cl = new URLClassLoader(urls, cl);
      }
    }
    catch (DependencyResolutionRequiredException e)
    {
      throw new MavenReportException("Error calculating scope classpath", e);
    }
    catch (MalformedURLException e)
    {
      throw new MavenReportException("Error calculating scope classpath", e);
    }

    return cl;
  }

  static public class URLCreationFactory extends AbstractObjectCreationFactory
  {
    public Object createObject(
      Attributes attributes) throws MalformedURLException
    {
      String href = attributes.getValue("href");
      if (href == null)
        throw new IllegalStateException("Missing href attribute");

      URL master = (URL)digester.getRoot();
      return new URL(master, href);
    }
  }

  static protected class SkipFilter extends ComponentFilter
  {
    protected boolean accept(
      ComponentBean component)
    {
      String componentType = component.getComponentType();

      // always skip API and base class generation
      return (!componentType.startsWith("javax") &&
              !componentType.endsWith("Base"));
    }
  }

  private class ComponentNamespaceFilter extends ComponentFilter
  {
    public ComponentNamespaceFilter()
    {
    }

    protected boolean accept(
      ComponentBean component)
    {
      if (component.getTagName() == null)
        return false;

      return taglibs.containsValue(component.getTagName().getNamespaceURI());
    }
  }

  private class ValidatorNamespaceFilter extends ValidatorFilter
  {
    public ValidatorNamespaceFilter()
    {
    }

    protected boolean accept(
      ValidatorBean component)
    {
      if (component.getTagName() == null)
        return false;

      return taglibs.containsValue(component.getTagName().getNamespaceURI());
    }
  }

  private class ConverterNamespaceFilter extends ConverterFilter
  {
    public ConverterNamespaceFilter()
    {
    }

    protected boolean accept(
      ConverterBean component)
    {
      if (component.getTagName() == null)
        return false;

      return taglibs.containsValue(component.getTagName().getNamespaceURI());
    }
  }

  static final protected class TagAttributeFilter extends PropertyFilter
  {
    protected boolean accept(
      PropertyBean property)
    {
      return (!property.isTagAttributeExcluded());
    }
  }

  static final protected class ComponentTagFilter extends ComponentFilter
  {
    protected boolean accept(
      ComponentBean component)
    {
      return (component.getTagName() != null);
    }
  }

  static final protected class ConverterTagFilter extends ConverterFilter
  {
    protected boolean accept(
      ConverterBean converter)
    {
      return (converter.getTagClass() != null);
    }
  }

  static final protected class ValidatorTagFilter extends ValidatorFilter
  {
    protected boolean accept(
      ValidatorBean validator)
    {
      return (validator.getTagClass() != null);
    }
  }

  final protected static class NonHiddenFilter extends PropertyFilter
  {
    protected boolean accept(
        PropertyBean property)
    {
      return (!property.isHidden());
    }
  }

  private FacesConfigBean _facesConfig;

  // todo: make this configurable?
  private boolean _attrDocSpansColumns = false;

  /**
   * Specifies the directory where the report will be generated
   *
   * @parameter default-value="${project.reporting.outputDirectory}"
   * @required
   */
  private File outputDirectory;

  /**
   * Directory where the original site is present.
   * (TRIED using ${baseDir}/src/site;  that inserted a 'null' into
   * the string for some reason.  TRIED using ${siteDirectory},
   * which was undefined.  TRIED ${project.directory}src/site; which also
   * inserted a null.  ${project.build.directory}/../src/site seems to work,
   * though it assumes that ${project.build.directory} is
   * ${project.directory}/target.
   *
   * @parameter default-value="${project.build.directory}/../src/site/"
   * @required
   */
  private File siteDirectory;

  /**
   * @parameter expression="${project}"
   * @required
   * @readonly
   */
  private MavenProject project;

  /**
   * @parameter
   * @required
   */
  private Map taglibs;

  /**
   * @parameter expression="META-INF/maven-faces-plugin/faces-config.xml"
   * @required
   * @readonly
   */
  private String resourcePath;


  /**
   * @component
   * @required
   * @readonly
   */
  private SiteRenderer siteRenderer;

  static private final String _DOC_SUBDIRECTORY = "tagdoc";
  static private final String[] _NON_DOCUMENTED_AGENTS = {"phone", "voice"};

}
