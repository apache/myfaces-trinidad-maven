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
package org.apache.myfaces.trinidadbuild.plugin.jdeveloper;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;

import org.w3c.dom.Node;

import org.w3c.dom.NodeList;

import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class TldContentHandler
{
  /**
    * Content Handler.
    */
  public TldContentHandler()
  {
  }

  /**
    * Parse the .tld file to get the information
    * needed for the .jpr
    */
  public void parseTld(File file)
  throws SAXException,
         IOException,
         ParserConfigurationException
  {
    // Create a builder factory
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setValidating(false);

    // Create the Builder and parse the file
    DocumentBuilder docBuilder = factory.newDocumentBuilder();
    
    // Set Entity Resolver to resolve external entities, i.e.
    // the "http://...." in the <!DOCTYPE tag
    EntityResolver entityResolver = new PluginEntityResolver();
    docBuilder.setEntityResolver(entityResolver);
    Document document = docBuilder.parse(file);
    
    _processTldNodes(document);
  }

  /*=========================================================================
   * Gettors and settors
   * ======================================================================*/

  public void setVersion(String version)
  {
    _version = version;
  }

  public void setName(String name)
  {
    _name = name;
  }

  public void setPrefix(String prefix)
  {
    _prefix = prefix;
  }

  public void setURI(String uri)
  {
    _uri = uri;
  }

  public void setJspVersion(String jspVersion)
  {
    _jspVersion = jspVersion;
  }

  public String getVersion()
  {
    return _version == null? "" : _version;
  }

  public String getName()
  {
    return _name == null? "" : _name;
  }

  public String getPrefix()
  {
    return _prefix == null? "" : _prefix;
  }

  public String getURI()
  {
    return _uri == null? "" : _uri;
  }

  public String getJspVersion()
  {
    return _jspVersion == null? "" : _jspVersion;
  }


  /**
    * Find all the TLD nodes we want, get each node's value
    * and set the value on the proper class property.
    *
    * @param document  - DOM Document from the TLD file
    */
  private void _processTldNodes(Document document)
  {
    Node node = null;

    // Get the Nodes first node.  We can be specific here
    // because we know we want the first node.
    NodeList nodeList = document.getElementsByTagName(_TLIB_VERSION);
    if (nodeList != null && nodeList.getLength() != 0)
    {
      node = nodeList.item(0);
      setVersion(node.getFirstChild().getNodeValue());
    }

    nodeList = document.getElementsByTagName(_JSP_VERSION);
    if (nodeList != null && nodeList.getLength() != 0)
    {
      node = nodeList.item(0);
      setJspVersion(node.getFirstChild().getNodeValue());
    }

    // Must go before _DISPLAY_NAME
    nodeList = document.getElementsByTagName(_SHORT_NAME);
    if (nodeList != null && nodeList.getLength() != 0)
    {
      node = nodeList.item(0);
      setPrefix(node.getFirstChild().getNodeValue());
    }

    // Must go after _SHORT_NAME
    nodeList = document.getElementsByTagName(_DISPLAY_NAME);
    if (nodeList != null && nodeList.getLength() != 0)
    {
      node = nodeList.item(0);
      setName(node.getFirstChild().getNodeValue());
    }
    else
    {
      setName(getPrefix());
    }

    nodeList = document.getElementsByTagName(_URI);
    if (nodeList != null && nodeList.getLength() != 0)
    {
      node = nodeList.item(0);
      setURI(node.getFirstChild().getNodeValue());
    }
  }

  //========================================================================
  // Private variables
  //========================================================================

  private String _version    = null; // tlib-version
  private String _name       = null; // display-name
  private String _prefix     = null; // short-name
  private String _jspVersion = null; // jsp-version
  private String _uri        = null; // uri

  private final static String _TLIB_VERSION = "tlib-version"; //version NOTRANS
  private final static String _DISPLAY_NAME = "display-name"; //name NOTRANS
  private final static String _SHORT_NAME   = "short-name";   //prefix NOTRANS
  private final static String _JSP_VERSION  = "jsp-version";   //NOTRANS
  private final static String _URI          = "uri";
  

  /**
   * Gary Kind 01/22/2008. This class is used solely to get around a 
   * java.net.NoRouteToHostException that occurs in the tag libs 
   * <!DOCTYPE... tag, which is:
   * 
   * <!DOCTYPE taglib
   * PUBLIC "-//Sun Microsystems, Inc.//DTD JSP Tag Library 1.2//EN"
   * "http://java.sun.com/dtd/web-jsptaglibrary_1_2.dtd">
   * 
   * The http URL causes this exception for some unknown reason. I have
   * searched high and low on the web for a real solution and finally found
   * this workaround at 
   * http://forum.java.sun.com/thread.jspa?threadID=284209&forumID=34
   * Apparently a LOT of developers are seeing similar problems and they too
   * are not able to find a solution. This workaround works perfectly and all
   * is well.
   */
  private class PluginEntityResolver
    implements EntityResolver
  {
    public InputSource resolveEntity(String publicId, String systemId)
    {
      if ("-//Sun Microsystems, Inc.//DTD JSP Tag Library 1.2//EN".equals(publicId))
      {
        String xmlStr = "<?xml version='1.0' encoding='UTF-8'?>";
        byte[] buf = xmlStr.getBytes();
        ByteArrayInputStream bais = new ByteArrayInputStream(buf);
        return new InputSource(bais);
      }
      else 
        return null;
    }
  }

} // endclass TldContentHandler
