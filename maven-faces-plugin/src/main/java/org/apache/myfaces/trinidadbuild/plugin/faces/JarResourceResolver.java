package org.apache.myfaces.trinidadbuild.plugin.faces;

import java.io.InputStream;

import java.net.MalformedURLException;
import java.net.URL;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import javax.xml.transform.stream.StreamSource;

public /**
   * this resolver is to make sure imports of other stylesheets within the classpath get loaded
   */
  class JarResourceResolver implements URIResolver {
    public Source resolve(String href, String base) throws TransformerException {
      if (!href.startsWith("jar:")) 
      {
        try {
          URL defaultResolvedUrl = new URL(new URL(base), href);
          return new StreamSource(defaultResolvedUrl.toString());
        } 
        catch (MalformedURLException ex) 
        {
          throw new TransformerException(ex);
        }
      } 
      else 
      {
        try 
        {
          String resource = href.substring(4); // chop off the jar:
          InputStream is = getClass().getResourceAsStream(resource);
          return new StreamSource(is, resource);
        } 
        catch (Exception ex) 
        {
          throw new TransformerException(ex);
        }
      }
    }
  }
