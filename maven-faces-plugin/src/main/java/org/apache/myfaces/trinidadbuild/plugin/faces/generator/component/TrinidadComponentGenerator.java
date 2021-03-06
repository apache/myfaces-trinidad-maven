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
package org.apache.myfaces.trinidadbuild.plugin.faces.generator.component;

import java.io.IOException;

import java.lang.reflect.Modifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.maven.plugin.logging.Log;
import org.apache.myfaces.trinidadbuild.plugin.faces.io.PrettyWriter;
import org.apache.myfaces.trinidadbuild.plugin.faces.parse.ComponentBean;
import org.apache.myfaces.trinidadbuild.plugin.faces.parse.PropertyBean;
import org.apache.myfaces.trinidadbuild.plugin.faces.util.Filter;
import org.apache.myfaces.trinidadbuild.plugin.faces.util.FilteredIterator;
import org.apache.myfaces.trinidadbuild.plugin.faces.util.Util;


public class TrinidadComponentGenerator extends AbstractComponentGenerator
{
  public TrinidadComponentGenerator(Log log, boolean is12)
  {
    super(log, is12);
  }

  @Override
  protected void addSpecificImports(Set<String> imports, ComponentBean component)
  {
    // FacesBean is always needed to define the TYPE

    imports.add("org.apache.myfaces.trinidad.bean.FacesBean");

    Iterator<PropertyBean> properties = component.properties();
    properties = new FilteredIterator<PropertyBean>(properties, new NonVirtualFilter());
    
    // PropertyKey only needed if there are properties
    if (properties.hasNext())
    {
      imports.add("org.apache.myfaces.trinidad.bean.PropertyKey");

      Filter<PropertyBean> resolvable = new ResolvableTypeFilter();
      while (properties.hasNext())
      {
        PropertyBean property = properties.next();

        // ComponentUtils only needed for resolvable properties
        if (resolvable.accept(property))
        {
          imports.add("org.apache.myfaces.trinidad.util.ComponentUtils");
        }
        if (property.isNoOp())
        {
          imports.add("org.apache.myfaces.trinidad.logging.TrinidadLogger");
        }
      }
    }
  }

  @Override
  protected void writeConstructorContent(PrettyWriter out,
                                         ComponentBean component,
                                         int modifiers,
                                         String rendererType) throws IOException
  {
    out.println("super(" + rendererType + ");");
  }

  @Override
  protected void writePropertyListMethods(PrettyWriter out, PropertyBean property, Collection gnoreList)
  {
    // nothing
  }

  public void writePropertyConstants(
      PrettyWriter out,
      String superclassName,
      ComponentBean component) throws IOException
  {
    out.println("static public final FacesBean.Type TYPE = new FacesBean.Type(");
    out.indent();
    out.println(superclassName + ".TYPE);");
    out.unindent();

    //  component property keys
    Iterator<PropertyBean> properties = component.properties();
    properties = new FilteredIterator<PropertyBean>(properties, new NonVirtualFilter());
    properties = new FilteredIterator<PropertyBean>(properties, new NonOverriddenFilter());

    while (properties.hasNext())
    {
      PropertyBean property = properties.next();
      String propName = property.getPropertyName();
      String propKey = Util.getConstantNameFromProperty(propName, "_KEY");
      String propAlias = property.getAliasOf();

      if (property.getDeprecated() != null)
      {
        out.println("@Deprecated");
      }

      out.println("static public final PropertyKey " + propKey + " =");
      out.indent();
      if (propAlias != null)
      {
        String aliasKey = Util.getConstantNameFromProperty(propAlias, "_KEY");
        out.print("TYPE.registerAlias(" + aliasKey + ", \"" + propName + "\");");
      }
      else
      {
        out.print("TYPE.registerKey(\"" + propName + "\"");

        // property class
        String propFullClass = property.getPropertyClass();
        String propClass = Util.getClassFromFullClass(propFullClass);
        if (propClass == null)
        {
          propClass = "String";
        }
        String propDefault = property.getDefaultValue();
        String propMutable = _getPropertyMutable(property);

        if (!"Object".equals(propClass) || propDefault != null)
        {
          // TODO: do not use boxed class here
          String boxedClass = Util.getBoxedClass(propClass);
          out.print(", " + boxedClass + ".class");
        }
        else if (propMutable != null)
        {
          out.print(", Object.class");
        }

        if (propDefault != null)
        {
          if (property.isEnum())
            out.print(", Enum.valueOf(" + propClass + ".class," +
                      convertStringToBoxedLiteral("String", propDefault) +
                      ")");
          else
            out.print(", " + convertStringToBoxedLiteral(propClass, propDefault));
        }
        else if (propMutable != null)
        {
          out.print(", null");
        }

        // property capabilities
        String propCaps = _getPropertyCapabilities(property);

        if (propCaps != null)
          out.print(", " + propCaps);

        if (propMutable != null)
        {
          if (propCaps == null )
            out.print(", 0");

          out.print(", " + propMutable);
        }


        out.println(");");
      }
      out.unindent();
    }
  }

  @Override
  protected void writePropertyDeclaration(PrettyWriter out, PropertyBean property) throws IOException
  {
    // nothing by default
  }

  /**
   * Whether the getters/setters have the final modifier
   *
   * @return true if the getters/setters are final
   */
  @Override
  protected boolean isAccessorMethodFinal()
  {
    return true;
  }

  @Override
  protected void writePropertySetterMethodBody(PrettyWriter out,
                                               PropertyBean property,
                                               String propertyClass) throws IOException
  {
    String propName = property.getPropertyName();
    String propKey = Util.getConstantNameFromProperty(propName, "_KEY");
    String propVar = Util.getVariableFromName(propName);

    if (!property.isNoOp())
    {
      if (Util.isPrimitiveClass(propertyClass))
      {
        out.println("setProperty(" + propKey + ", " +
                  convertVariableToBoxedForm(propertyClass, propVar) +
                  ");");
      }
      else
      {
        out.println("setProperty(" + propKey + ", (" + propVar + "));");
      }
    }
    else
    {
      out.println("TrinidadLogger log = TrinidadLogger.createTrinidadLogger(this.getClass());");
      out.print("log.warning(\"property \\\"" + propName + "\\\" is ");
      out.print("using a no-op implementation. Used in extreme cases when the property value, beyond the default value, results in unwanted behavior.");
      out.println("\");");
    }
  }

  @Override
  protected void writePropertyGetterMethodBody(
      PrettyWriter out,
      PropertyBean property) throws IOException
  {
    String propName = property.getPropertyName();
    String propertyFullClass = property.getPropertyClass();
    String propertyClass = Util.getClassFromFullClass(propertyFullClass);
    String propKey = Util.getConstantNameFromProperty(propName, "_KEY");

    String resolvableType = resolveType(propertyFullClass);

    if (resolvableType != null)
    {
      // TODO: change signature of ComponentUtils.resolveCharacter
      //       to take Object instead of Character
      if (resolvableType.equals("Character"))
      {
        out.println("return ComponentUtils.resolveCharacter((Character)getProperty(" + propKey + "));");
      }
      else
      {
        // TODO: stop specifying default values in the getters
        String resolveMethod = Util.getPrefixedPropertyName("resolve", resolvableType);
        String propertyDefault = property.getDefaultValue();
        out.print("return ComponentUtils." + resolveMethod + "(getProperty(" + propKey + ")");
        if (propertyDefault != null)
        {
          out.print(", " + Util.convertStringToLiteral(propertyClass,
              propertyDefault));
        }
        out.println(");");
      }
    }
    else
    {
      if (propertyClass.equals("Object"))
      {
        // Cast is not necessary if the property class is Object
        out.println("return getProperty(" + propKey + ");");
      }
      else
      {
        out.println("return (" + propertyClass + ")" +
            "getProperty(" + propKey + ");");
      }
    }
  }

  @Override
  public void writeStateManagementMethods(PrettyWriter out, ComponentBean component) throws IOException
  {
    // nothing to do here
  }

  @Override
  public void writePropertyListMethods(
      PrettyWriter out,
      PropertyBean property) throws IOException
  {
    String propName = property.getPropertyName();
    String propKey = Util.getConstantNameFromProperty(propName, "_KEY");
    String propertyClass = property.getPropertyClass();
    if (!"java.util.List".equals(propertyClass))
    {
      getLog().error("Invalid list type: " + propertyClass);
      return;
    }

    // Look for the generic type - if it doesn't exist, then
    // we'll be an Object.
    String[] params = property.getPropertyClassParameters();
    if ((params == null) || (params.length == 0))
      propertyClass = "java.lang.Object";
    else
      propertyClass = params[0];

    propertyClass = Util.getClassFromFullClass(propertyClass);

    String singularName = getSingular(propName);
    String propVar = Util.getVariableFromName(singularName);
    String description = property.getDescription();
    String addMethod = Util.getPrefixedPropertyName("add", singularName);
    String removeMethod = Util.getPrefixedPropertyName("remove", singularName);
    String getMethod = Util.getPrefixedPropertyName("get", propName);

    out.println();
    out.println("/**");
    if (description != null)
    {
      out.println(" * Adds a " + convertMultilineComment(description));
    }
    out.println(" */");
    if (property.getDeprecated() != null)
    {
      out.println("@Deprecated");
    }
    out.println("final public void " + addMethod + "(" + propertyClass + " " +
        propVar + ")");
    out.println("{");
    out.indent();
    out.println("if (" + propVar + " == null)");
    out.println("  throw new NullPointerException();");
    out.println();
    out.println("getFacesBean().addEntry(" + propKey + ", " + propVar + ");");
    out.unindent();
    out.println("}");

    out.println();
    out.println("/**");
    if (description != null)
    {
      out.println(" * Removes a " + convertMultilineComment(description));
    }
    out.println(" */");
    out.println("final public void " + removeMethod + "(" + propertyClass + " " +
        propVar + ")");
    out.println("{");
    out.indent();
    out.println("if (" + propVar + " == null)");
    out.println("  throw new NullPointerException();");
    out.println();
    out.println("getFacesBean().removeEntry(" + propKey + ", " + propVar + ");");
    out.unindent();
    out.println("}");

    out.println();
    out.println("/**");
    if (description != null)
    {
      out.println(" * Gets all " + convertMultilineComment(description));
    }
    out.println(" */");
    if (property.getDeprecated() != null)
    {
      out.println("@Deprecated");
    }
    out.println("final public " + propertyClass + "[] " + getMethod + "()");
    out.println("{");
    out.indent();
    out.println("return (" + propertyClass + "[]) getFacesBean().getEntries(");
    out.println("         " + propKey + ", " + propertyClass + ".class);");
    out.unindent();
    out.println("}");
  }

  @Override
  public void writeOther(PrettyWriter out, ComponentBean component, String overrideClassName) 
  throws IOException
  {
    _writeGetBeanType(out);

    writeConstructor(out, component, overrideClassName, Modifier.PROTECTED);

    _writeTypeLock(out, component);
  }

  protected void _writeGetBeanType(
      PrettyWriter out) throws IOException
  {
    out.println();
    out.println("@Override");
    out.println("protected FacesBean.Type getBeanType()");
    out.println("{");
    out.indent();
    out.println("return TYPE;");
    out.unindent();
    out.println("}");
  }

  private void _writeTypeLock(
      PrettyWriter out, ComponentBean component) throws IOException
  {
    out.println();
    out.println("static");
    out.println("{");
    out.indent();
    String rendererType = component.getRendererType();
    if (rendererType == null)
    {
      out.println("TYPE.lock();");
    }
    else
    {
      String componentFamily = component.findComponentFamily();
      out.println("TYPE.lockAndRegister(\"" + componentFamily + "\"," +
          "\"" + rendererType + "\");");
    }

    out.unindent();
    out.println("}");
  }

  private String _getPropertyMutable(
    PropertyBean property)
  {
    String mutable = property.getMutable();

    if (mutable == null || "immutable".equals(mutable))
      return null;

    if ("rarely".equals(mutable))
    {
      return "PropertyKey.Mutable.RARELY";
    }
    else if ("sometimes".equals(mutable))
    {
      return "PropertyKey.Mutable.SOMETIMES";
    }
    else if ("often".equals(mutable))
    {
      return "PropertyKey.Mutable.OFTEN";
    }

    throw new IllegalArgumentException("unknown mutable property \"" + mutable + "\", supported types are immutable, rarely, sometimes, and often");

  }

  private String _getPropertyCapabilities(
      PropertyBean property)
  {
    List<String> caps = new ArrayList<String>();

    if (property.isMethodBinding() ||
        property.isLiteralOnly())
    {
      caps.add("PropertyKey.CAP_NOT_BOUND");
    }

    if (property.isStateHolder())
    {
      caps.add("PropertyKey.CAP_STATE_HOLDER");
    }

    if (property.isTransient())
    {
      caps.add("PropertyKey.CAP_TRANSIENT");
    }

    if (property.isList())
    {
      caps.add("PropertyKey.CAP_LIST");
    }

    if (caps.isEmpty())
      return null;

    StringBuffer sb = new StringBuffer();
    for (int i = 0; i < caps.size(); i++)
    {
      if (i > 0)
        sb.append(" | ");
      sb.append(caps.get(i));
    }
    return sb.toString();
  }
}
