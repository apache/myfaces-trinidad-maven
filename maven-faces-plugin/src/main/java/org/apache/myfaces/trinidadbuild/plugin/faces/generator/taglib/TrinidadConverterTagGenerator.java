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
package org.apache.myfaces.trinidadbuild.plugin.faces.generator.taglib;

import org.apache.maven.plugin.logging.Log;
import org.apache.myfaces.trinidadbuild.plugin.faces.io.PrettyWriter;
import org.apache.myfaces.trinidadbuild.plugin.faces.parse.AbstractTagBean;
import org.apache.myfaces.trinidadbuild.plugin.faces.parse.ConverterBean;
import org.apache.myfaces.trinidadbuild.plugin.faces.parse.PropertyBean;
import org.apache.myfaces.trinidadbuild.plugin.faces.util.Util;

import java.util.Set;
import java.util.TreeSet;

public class TrinidadConverterTagGenerator extends AbstractConverterTagGenerator {

  public TrinidadConverterTagGenerator(boolean is12, String licenseHeader, Log log) {
    super(is12, licenseHeader, log);
  }

  protected Set<String> createImports(ConverterBean converter) {
    Set<String> imports = new TreeSet<String>();

    if (is12())
    {
      imports.add("org.apache.myfaces.trinidad.webapp.TrinidadConverterELTag");
      imports.add("javax.faces.context.FacesContext");
      imports.add("javax.faces.application.Application");
    }
    else
    {
      imports.add("javax.faces.webapp.ConverterTag");
    }

    imports.add("javax.servlet.jsp.JspException");
    imports.add(converter.getConverterClass());

    imports.add("javax.faces.convert.Converter");
    if (is12())
    {
      imports.add("javax.el.ValueExpression");
    }
    else
    {
      imports.add("javax.faces.el.ValueBinding");
    }
    imports.add("org.apache.myfaces.trinidadinternal.taglib.util.TagUtils");


    addImportsFromPropertes(converter, imports);
    return imports;
  }

  @Override
  protected void writeClass(PrettyWriter out, AbstractTagBean abstractTag)
  {
    String className = Util.getClassFromFullClass(abstractTag.getTagClass());
    if (is12())
    {
      out.println("public class " + className +
                  " extends TrinidadConverterELTag");
      out.println("{");
      out.indent();
    }
    else
    {
      // non 1.2
      super.writeClass(out, abstractTag);
    }
  }


  protected void writeSetProperty(
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

    if (is12())
    {
      out.println("if (!" + propVar + ".isLiteralText())");
      out.println("{");
      out.indent();
      out.println("converter.setValueExpression(\"" + propName + "\", " +
                  propVar + ");");
      out.unindent();
      out.println("}");
      String propType = resolveType(propFullClass);
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

        if ("Enum".equals (propType))
        {
          out.println(propClass + " value = Enum.valueOf(" + propClass + ".class, " + propVar + ".getExpressionString());");
        }
        else
        {
          out.println(propClass + " value = TagUtils.get" + propType + "(" + propVar + ".getValue(FacesContext.getCurrentInstance().getELContext()));");
        }
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
      String propType = resolveType(propFullClass);
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

        if ("Enum".equals (propType))
        {
          out.println(propClass + " value = Enum.valueOf(" + propClass + ".class, " + propVar + ");");
        }
        else
        {
          out.println(propClass + " value = TagUtils.get" + propType + "(" + propVar + ");");
        }
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

}
