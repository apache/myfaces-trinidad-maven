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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.plugin.logging.Log;
import org.apache.myfaces.trinidadbuild.plugin.faces.generator.GeneratorHelper;
import org.apache.myfaces.trinidadbuild.plugin.faces.io.PrettyWriter;
import org.apache.myfaces.trinidadbuild.plugin.faces.parse.ComponentBean;
import org.apache.myfaces.trinidadbuild.plugin.faces.parse.EventBean;
import org.apache.myfaces.trinidadbuild.plugin.faces.parse.EventRefBean;
import org.apache.myfaces.trinidadbuild.plugin.faces.parse.FacetBean;
import org.apache.myfaces.trinidadbuild.plugin.faces.parse.PropertyBean;
import org.apache.myfaces.trinidadbuild.plugin.faces.util.Filter;
import org.apache.myfaces.trinidadbuild.plugin.faces.util.FilteredIterator;
import org.apache.myfaces.trinidadbuild.plugin.faces.util.SourceTemplate;
import org.apache.myfaces.trinidadbuild.plugin.faces.util.Util;


public abstract class AbstractComponentGenerator implements ComponentGenerator
{

  private Log _log;
  boolean _is12;

  public AbstractComponentGenerator(Log log, boolean is12)
  {
    _log = log;
    _is12 = is12;
  }

  protected Log getLog()
  {
    return _log;
  }

  @Override
  public void writeClassBegin(
      PrettyWriter out,
      String className,
      String superclassName,
      ComponentBean component,
      SourceTemplate template,
      boolean createSuperclass)
  {
    if (className == null)
      throw new NullPointerException();
    
    out.println("/**");

    // TODO: restore description (needs escaping?)
//    String description = component.getDescription();
//    if (description != null)
//    {
//      out.println(" *");
//      out.println(" * " + convertMultilineComment(description));
//    }

    String longDescription = component.getLongDescription();
    if (longDescription != null)
    {
      out.println(" *");
      out.println(" * " + convertMultilineComment(longDescription));
    }

    if (component.hasEvents(true))
    {
      // the events javadoc
      out.println(" *");
      out.println(" * <h4>Events:</h4>");
      out.println(" * <table border=\"1\" width=\"100%\" cellpadding=\"3\" summary=\"\">");
      out.println(" * <tr bgcolor=\"#CCCCFF\" class=\"TableHeadingColor\">");
      out.println(" * <th align=\"left\">Type</th>");
      out.println(" * <th align=\"left\">Phases</th>");
      out.println(" * <th align=\"left\">Description</th>");
      out.println(" * </tr>");
      Iterator<EventRefBean> events = component.events(true);
      while (events.hasNext())
      {
        EventRefBean eventRef = events.next();
        EventBean event = eventRef.resolveEventType();
        if (event != null)
        {
          String eventClass = event.getEventClass();
          String[] eventPhases = eventRef.getEventDeliveryPhases();
          String eventDescription = event.getDescription();
          out.println(" * <tr class=\"TableRowColor\">");
          out.println(" * <td valign=\"top\"><code>" + eventClass + "</code></td>");
          out.print(" * <td valign=\"top\" nowrap>");
          if (eventPhases != null)
          {
            for (int i = 0; i < eventPhases.length; i++)
            {
              if (i > 0)
                out.print("<br>");
              out.print(eventPhases[i]);
            }
          }
          out.println("</td>");
          out.println(" * <td valign=\"top\">" + eventDescription + "</td>");
          out.println(" * </tr>");
        }
      }
      out.println(" * </table>");
    }

    if (!component.hasChildren())
    {
      out.println(" * <p>");
      out.println(" * It does not support any children.");
    }

    String deprecatedMessage = component.getDeprecated();
    if (deprecatedMessage != null)
    {
      out.println(" * @deprecated " + convertMultilineComment(deprecatedMessage));
    }
    out.println(" */");

    if (deprecatedMessage != null)
    {
      out.println("@Deprecated");
    }

    // TODO: eliminate <mfp:component-class-modifier> metadata
    int modifiers = component.getComponentClassModifiers();
    
    // make abstract superclass classes abstract and package private
    if (createSuperclass)
    {
      // we would really like to make this package private, but because of a bug in the Java
      // Introspection code, a get on a final method of a public class inherited from a
      // package private class actually refers to the package private class.  The result
      // is that invoking it blows up.  Therefore, instead of making the class package private,
      // we have to make it public. GRRR.
    
      // remove all of the access modifiers to make this package private
      modifiers &= ~(Modifier.PRIVATE | Modifier.PROTECTED); // Modifier.PUBLIC
      
      // force abstract on as well as public, due to stupid bug
      modifiers |= Modifier.ABSTRACT | Modifier.PUBLIC;
    }
    else
    {
      // if no modifier is specified, default to public
      if ((modifiers & (Modifier.PUBLIC | Modifier.PRIVATE | Modifier.PROTECTED)) == 0)
      {
        modifiers |= Modifier.PUBLIC;
      }
    }
    
    String classStart = Modifier.toString(modifiers);
    // TODO: use canonical ordering
    classStart = classStart.replaceAll("public abstract", "abstract public");
    out.println(classStart + " class " + className +
        " extends " + superclassName);

    Set<String> interfaces = new HashSet<String>();
    if (template != null)
      interfaces.addAll(template.getImplements());

    if (component.isNamingContainer())
      interfaces.add("javax.faces.component.NamingContainer");

    if (component.isClientBehaviorHolder())
      interfaces.add("javax.faces.component.behavior.ClientBehaviorHolder");

    Iterator<EventRefBean> events = component.events();
    while (events.hasNext())
    {
      EventRefBean eventRef = events.next();
      EventBean event = eventRef.resolveEventType();
      if (event != null)
      {
        if (!eventRef.isIgnoreSourceInterface())
        {
          String source = event.getEventSourceInterface();
          if (source != null)
            interfaces.add(Util.getClassFromFullClass(source));
        }
      }
    }

    if (!interfaces.isEmpty())
    {
      Set<String> implementsSet = new HashSet<String>();
      for (Iterator iter = interfaces.iterator(); iter.hasNext();)
      {
        String fcqn = (String) iter.next();
        implementsSet.add(Util.getClassFromFullClass(fcqn));
      }

      // implements clause spans multiple lines
      char[] indent = new char[classStart.length() +
          " class ".length() +
          className.length() + 1];
      Arrays.fill(indent, ' ');
      out.print(indent);
      out.print("implements ");
      for (Iterator iter = implementsSet.iterator(); iter.hasNext();)
      {
        out.print((String) iter.next());
        if (iter.hasNext())
        {
          out.println(",");
          out.print(indent);
          out.print("           ");  // same length as "implements "
        }
      }
      out.println();
    }

    out.println("{");
    out.indent();
  }

  @Override
  public void writeClassEnd(
      PrettyWriter out)
  {
    out.unindent();
    out.println("}");
  }

  @Override
  public void writeImports(PrettyWriter out, SourceTemplate template, String packageName, String fullSuperclassName,
                           String superclassName, Collection components)
  {
    throw new UnsupportedOperationException("not implemented");
  }

  @Override
  public void writeImports(
      PrettyWriter out,
      SourceTemplate template,
      String packageName,
      String fullSuperclassName,
      String superclassName,
      ComponentBean component)
  {
    Set<String> imports = new TreeSet<String>();

    // Use the template imports
    if (template != null)
      imports.addAll(template.getImports());

    // Detect NamingContainer
    if (component.isNamingContainer())
      imports.add("javax.faces.component.NamingContainer");

    // Detect ClientBehaviorHolder
    if (component.isClientBehaviorHolder())
    {
      imports.add("java.util.Arrays");
      imports.add("java.util.Collection");
      imports.add("java.util.Collections");
      imports.add("java.util.List");
      imports.add("java.util.Map");
      imports.add("javax.faces.component.behavior.ClientBehavior");
      imports.add("javax.faces.component.behavior.ClientBehaviorHolder");
    }

    Iterator<PropertyBean> properties = component.properties();
    properties = new FilteredIterator<PropertyBean>(properties, new NonVirtualFilter());
    // PropertyKey only needed if there are properties
    if (properties.hasNext())
    {
      while (properties.hasNext())
      {
        PropertyBean property = properties.next();
        String propertyClass = property.getPropertyClass();
        if (propertyClass != null)
        {
          imports.add(propertyClass);
          // Check for generics
          String[] types = property.getAttributeClassParameters();
          if (types != null)
          {
            for (int i = types.length - 1; i >= 0; i--)
            {
              addGenericImports(imports, types[i]);
            }
          }
        }
      }
    }

    Iterator facets = component.facets();
    // UIComponent needed if there are facets
    if (facets.hasNext())
      imports.add("javax.faces.component.UIComponent");

    Iterator<EventRefBean> events = component.events();
    while (events.hasNext())
    {
      EventRefBean eventRef = events.next();
      EventBean event = eventRef.resolveEventType();

      if (event == null)
      {
        getLog().warn("Unknown event type \"" + eventRef.getEventType() + "\"" +
            " in component:" + component.getComponentType());
      }
      else
      {
        String listenerClass = event.getEventListenerClass();
        if (listenerClass != null)
          imports.add(listenerClass);

        if (!eventRef.isIgnoreSourceInterface())
        {
          String sourceInterface = event.getEventSourceInterface();
          if (sourceInterface != null)
            imports.add(sourceInterface);
        }
      }
    }

    // Import causes a collision if className and superclassName are equal
    if (!superclassName.equals(fullSuperclassName))
    {
      String superPackageName = Util.getPackageFromFullClass(fullSuperclassName);
      // component superclass only needed if not in
      // same package as component class
      if (superPackageName != packageName)
        imports.add(fullSuperclassName);
    }

    // add other imports (generator specific)
    addSpecificImports(imports, component);

    // do not import implicit types!
    imports.removeAll(Util.PRIMITIVE_TYPES);

    GeneratorHelper.writeImports(out, packageName, imports);
  }

  protected void addSpecificImports(@SuppressWarnings("unused") Set<String> imports,
                                    @SuppressWarnings("unused") ComponentBean component)
  {
    // nothing by default
  }

  public void addGenericImports(Set<String> imports, String type)
  {
    Matcher matcher = _GENERIC_TYPE.matcher(type);
    if (matcher.matches())
    {
      // Generic type
      imports.add(matcher.group(1));
      String[] types = matcher.group(2).split(",");
      for (int i = types.length - 1; i >= 0; i--)
      {
        addGenericImports(imports, types[i]);
      }
    }
    else
    {
      // Non-generic type
      imports.add(type);
    }
  }

  @Override
  public void writeGenericConstants(
      PrettyWriter out,
      String componentFamily,
      String componentType) throws IOException
  {
    out.println();
    out.println("static public final String COMPONENT_FAMILY =");
    out.println("  \"" + componentFamily + "\";");
    out.println("static public final String COMPONENT_TYPE =");
    out.println("  \"" + componentType + "\";");
  }

  @Override
  public void writePropertyConstants(
      PrettyWriter out,
      String superclassName,
      ComponentBean component) throws IOException
  {
    // nothing
  }

  @Override
  public void writePropertyValueConstants(
      PrettyWriter out,
      ComponentBean component) throws IOException
  {
    //  component property keys
    Iterator<PropertyBean> properties = component.properties();
    properties = new FilteredIterator<PropertyBean>(properties, new NonVirtualFilter());
    while (properties.hasNext())
    {
      PropertyBean property = properties.next();
      String[] propertyValues = property.getPropertyValues();

      if (propertyValues != null)
      {
        String propName = property.getPropertyName();

        for (int i = 0; i < propertyValues.length; i++)
        {
          String propValue = propertyValues[i];
          String propValueName = propName +
              Character.toUpperCase(propValue.charAt(0)) +
              propValue.substring(1);
          String propValueKey = Util.getConstantNameFromProperty(propValueName);

          out.println("static public final String " + propValueKey + " = \"" + propValue + "\";");
        }

      }
    }
  }

  public void writeFacetConstants(
      PrettyWriter out,
      ComponentBean component) throws IOException
  {
    Iterator<FacetBean> facets = component.facets();
    while (facets.hasNext())
    {
      FacetBean facet = facets.next();
      String facetName = facet.getFacetName();
      String facetKey = Util.getConstantNameFromProperty(facetName, "_FACET");
      out.println("static public final " +
          "String " + facetKey + " = \"" + facetName + "\";");
    }
  }

  protected String convertStringToBoxedLiteral(
      String className,
      String value)
  {
    if (value == null)
    {
      return null;
    }
    else if ("String".equals(className))
    {
      return "\"" + value.replaceAll("\'", "\\'") + "\"";
    }
    else if ("boolean".equals(className))
    {
      return ("true".equals(value)) ? "Boolean.TRUE" : "Boolean.FALSE";
    }
    else if ("char".equals(className))
    {
      return "Character.valueOf('" + value.replaceAll("\'", "\\'") + "')";
    }
    else if ("int".equals(className))
    {
      return "Integer.valueOf(" + value + ")";
    }
    else if ("double".equals(className))
    {
      return "Double.valueOf(" + value + ")";
    }
    else if ("float".equals(className))
    {
      return "Float.valueOf(" + value + ")";
    }
    else if ("Number".equals(className))
    {
      if(value.indexOf(".") == -1)
      {
        return "Integer.valueOf(" + value + ")";
      }
      else
      {
        return "Double.valueOf(" + value + ")";
      }
    }
    else
    {
      throw new IllegalStateException("property-class " + className + " not supported for auto-boxing");
    }
  }

  protected String convertVariableToBoxedForm(
    String  className,
    String  varName)
  {
    if ("boolean".equals(className))
    {
      return varName + " ? Boolean.TRUE : Boolean.FALSE";
    }
    else if ("char".equals(className))
    {
      return "Character.valueOf(" + varName + ")";
    }
    else if ("int".equals(className))
    {
      return "Integer.valueOf(" + varName + ")";
    }
    else if ("short".equals(className))
    {
      return "Short.valueOf(" + varName + ")";
    }
    else if ("long".equals(className))
    {
      return "Long.valueOf(" + varName + ")";
    }
    else if ("double".equals(className))
    {
      return "Double.valueOf(" + varName + ")";
    }
    else if ("float".equals(className))
    {
      return "Float.valueOf(" + varName + ")";
    }
    else
    {
      throw new IllegalStateException("property-class " + className + " not supported for auto-boxing");
    }
  }

  @Override
  public void writeConstructor(
      PrettyWriter out,
      ComponentBean component,
      String overrideClassName,
      int modifiers) throws IOException
  {    
    String className;
        
    if (overrideClassName != null)
    {
      className  = overrideClassName;
    }
    else
    {
      String fullClassName = component.getComponentClass();
      className            = Util.getClassFromFullClass(fullClassName);
    }
    
    int classModifiers = component.getComponentClassModifiers();
    boolean isAbstract = Modifier.isAbstract(classModifiers);
    boolean isPackagePrivate = (modifiers & 
                                (Modifier.PRIVATE |  Modifier.PROTECTED | Modifier.PUBLIC)) == 0;
    
    if (Modifier.isProtected(modifiers))
    {
      out.println();
      out.println("/**");
      // TODO: restore this more descriptive comment with param docs
      //out.println(" * Construct an instance of the " + className);
      //out.println(" * with the specified renderer type.");
      //out.println(" * ");
      //out.println(" * @param rendererType  the renderer type");
      out.println(" * Construct an instance of the " + className + ".");
      out.println(" */");
      out.println("protected " + className + "(");
      out.indent();
      out.println("String rendererType");
      out.println(")");
      out.unindent();
      out.println("{");
      out.indent();

      writeConstructorContent(out, component, modifiers, "rendererType");

      out.unindent();
      out.println("}");

      // TODO: eliminate this inconsistency
      if (isAbstract)
      {
        out.println();
        out.println("/**");
        // TODO: restore this correctly phrased comment (tense vs. command)
        //out.println(" * Constructs an instance of " + className + ".");
        out.println(" * Construct an instance of the " + className + ".");
        out.println(" */");
        out.println("protected " + className + "()");
        out.println("{");
        out.indent();
        out.println("this(null);");
        out.unindent();
        out.println("}");
      }
    }
    else if ((Modifier.isPublic(modifiers) && !isAbstract) || isPackagePrivate)
    {
      String accessControl = Modifier.isPublic(modifiers)
        ? (isAbstract) ? "protected " : "public "
        : ""; // package private
      
      String rendererType = component.getRendererType();

      if (rendererType != null)
        rendererType = convertStringToBoxedLiteral("String", rendererType);

      out.println();
      out.println("/**");
      // TODO: restore this correctly phrased comment (tense vs. command)
      //out.println(" * Constructs an instance of " + className + ".");
      out.println(" * Construct an instance of the " + className + ".");
      out.println(" */");
            
      out.println(accessControl + className + "()");
      out.println("{");
      out.indent();

      writeConstructorContent(out, component, modifiers, rendererType);

      out.unindent();
      out.println("}");
    }
  }

  protected abstract void writeConstructorContent(
      PrettyWriter out,
      ComponentBean component,
      int modifiers, String rendererType) throws IOException;

  public void writeGetFamily(
      PrettyWriter out) throws IOException
  {
    out.println();
    out.println("@Override");
    out.println("public String getFamily()");
    out.println("{");
    out.indent();
    out.println("return COMPONENT_FAMILY;");
    out.unindent();
    out.println("}");
  }

  public void writePropertyMethods(
      PrettyWriter out,
      ComponentBean component) throws IOException
  {
    writePropertyMethods(out, component, null);
  }

  public void writePropertyMethods(PrettyWriter out, ComponentBean component, Collection ignoreList)
      throws IOException
  {
    Iterator<PropertyBean> properties = component.properties();
    properties = new FilteredIterator<PropertyBean>(properties, new NonVirtualFilter());
    if (isAccessorMethodFinal())
    {
      // Do not generate property methods if they are final and the properties are overrides
      properties = new FilteredIterator<PropertyBean>(properties, new NonOverriddenFilter());
    }
    while (properties.hasNext())
    {
      PropertyBean property = properties.next();
      if (property.isList())
        writePropertyListMethods(out, property, ignoreList);
      else
      {
        writePropertyDeclaration(out, property);
        writePropertyGet(out, property, ignoreList);
        writePropertySet(out, property, ignoreList);
        if (GeneratorHelper.isValidator(property, _is12))
        {
          writePropertyListMethods(out, property, ignoreList);
        }
      }
    }
  }

  abstract protected void writePropertyListMethods(
      PrettyWriter out,
      PropertyBean property,
      Collection inoreList) throws IOException;

  abstract protected void writePropertyListMethods(
      PrettyWriter out,
      PropertyBean property) throws IOException;

  static protected String getSingular(String plural)
  {
    if (plural.endsWith("s"))
      return plural.substring(0, plural.length() - 1);
    return plural;
  }

  protected abstract void writePropertyDeclaration(
      PrettyWriter out,
      PropertyBean property) throws IOException;

  protected void writePropertySet(
      PrettyWriter out,
      PropertyBean property,
      Collection ignoreList) throws IOException
  {
    String propertyClass = Util.getPropertyClass(property);
    writePropertySet(out, property, propertyClass, ignoreList);

    if (property.getAlternateClass() != null)
    {
      String alternateClass = Util.getAlternatePropertyClass(property);
      writePropertySet(out, property, alternateClass, ignoreList);
    }
  }

  protected void writePropertySet(
      PrettyWriter out,
      PropertyBean property,
      String propertyClass,
      Collection ignoreList) throws IOException
  {
    String propName = property.getPropertyName();
    String propVar = Util.getVariableFromName(propName);
    String description = property.getDescription();
    String setMethod = Util.getPrefixedPropertyName("set", propName);
    if (ignoreList != null && ignoreList.contains(setMethod))
    {
      return;
    }
    out.println();
    out.println("/**");
    if (description != null)
    {
      out.println(" * Sets " + convertMultilineComment(description));
    }

    if (property.isRequired())
    {
      out.println(" * <p>");
      out.println(" * This is a required property on the component.");
    }
    out.println(" * ");
    out.println(" * @param " + Util.getVariableFromName(propName) + "  the new " + propName + " value");
    if (property.isMethodBinding() && _is12)
    {
      out.println(" * @deprecated");
    }

    if (property.getDeprecated() != null)
    {
      out.print(" * @deprecated ");
      out.println(convertMultilineComment(property.getDeprecated()));
    }

    out.println(" */");

    if (property.getDeprecated() != null)
    {
      out.println("@Deprecated");
    }

    if (isAccessorMethodFinal())
    {
      out.print("final ");
    }

    out.println("public void " + setMethod + "(" + propertyClass + " " + propVar + ")");
    out.println("{");
    out.indent();
    writePropertySetterMethodBody(out, property, propertyClass);
    out.unindent();
    out.println("}");
  }

  protected abstract void writePropertySetterMethodBody(
      PrettyWriter out,
      PropertyBean property,
      String propertyClass) throws IOException;

  protected void writePropertyGet(
      PrettyWriter out,
      PropertyBean property,
      Collection ignoreList) throws IOException
  {
    String propName = property.getPropertyName();
    String propertyFullClass = property.getPropertyClass();
    String propertyClass = Util.getClassFromFullClass(propertyFullClass);
    String description = property.getDescription();
    String getMethod = Util.getMethodReaderFromProperty(propName, propertyClass);
    if (ignoreList != null && ignoreList.contains(getMethod))
    {
      return;
    }
    boolean isUnchecked = false;
    String[] genericTypes = property.getPropertyClassParameters();
    if (genericTypes != null && genericTypes.length > 0)
    {
      isUnchecked = true;
      propertyClass = Util.getPropertyClass(property);
    }

    out.println();
    out.println("/**");
    if (description != null)
    {
      out.println(" * Gets " + convertMultilineComment(description));
    }
    if (property.isRequired())
    {
      out.println(" * <p>");
      out.println(" * This is a required property on the component.");
      out.println(" * </p>");
    }

    out.println(" *");
    out.println(" * @return  the new " + propName + " value");
    if (property.isMethodBinding() && _is12)
    {
      out.println(" * @deprecated");
    }

    if (property.getDeprecated() != null)
    {
      out.print(" * @deprecated ");
      out.println(convertMultilineComment(property.getDeprecated()));
    }

    out.println(" */");

    if (property.getDeprecated() != null)
    {
      out.println("@Deprecated");
    }

    if (isUnchecked)
    {
      out.println("@SuppressWarnings(\"unchecked\")");
    }

    if (isAccessorMethodFinal())
    {
      out.print("final ");
    }

    out.println("public " + propertyClass + " " + getMethod + "()");
    out.println("{");
    out.indent();

    writePropertyGetterMethodBody(out, property);

    out.unindent();
    out.println("}");
  }

  /**
   * Whether the getters/setters have the final modifier
   *
   * @return true if the getters/setters are final
   */
  protected boolean isAccessorMethodFinal()
  {
    return false;
  }

  protected abstract void writePropertyGetterMethodBody(
      PrettyWriter out,
      PropertyBean property) throws IOException;

  @Override
  public void writeFacetMethods(
      PrettyWriter out,
      ComponentBean component) throws IOException
  {
    Iterator<FacetBean> facets = component.facets();
    while (facets.hasNext())
    {
      FacetBean facet = facets.next();
      writeFacetGet(out, facet);
      writeFacetSet(out, facet);
    }
  }

  public void writeFacetSet(
      PrettyWriter out,
      FacetBean facet) throws IOException
  {
    String facetName = facet.getFacetName();
    // TODO: drop the unnecessary "Facet" suffix
    String facetVar = facetName + "Facet";
    String facetKey = Util.getConstantNameFromProperty(facetName, "_FACET");
    String setMethod = Util.getPrefixedPropertyName("set", facetName);
    String description = facet.getDescription();

    out.println();
    out.println("/**");
    if (description != null)
    {
      out.println(" * " + convertMultilineComment(description));
    }
    if (facet.isRequired())
    {
      out.println(" * <p>");
      out.println(" * This is a required facet on the component.");
    }
    // TODO: put this back in
    //out.println(" * ");
    //out.println(" * @param " + facetVar + "  the new " + facetName + " facet");
    out.println(" */");

    // Remove type safety warning since getFacets is not generics enabled
    // under JSF 1.1 spec
    // TODO: Remove this line when Trinidad switch to JSF 1.2
    out.println("@SuppressWarnings(\"unchecked\")");

    out.println("final public void " + setMethod + "(UIComponent " + facetVar + ")");
    out.println("{");
    out.indent();
    out.println("getFacets().put(" + facetKey + ", " + facetVar + ");");
    out.unindent();
    out.println("}");
  }

  public void writeFacetGet(
      PrettyWriter out,
      FacetBean facet) throws IOException
  {
    String facetName = facet.getFacetName();
    String facetKey = Util.getConstantNameFromProperty(facetName, "_FACET");
    String getMethod = Util.getPrefixedPropertyName("get", facetName);
    String description = facet.getDescription();

    out.println();
    out.println("/**");
    if (description != null)
    {
      out.println(" * " + convertMultilineComment(description));
    }
    if (facet.isRequired())
    {
      out.println(" * <p>");
      out.println(" * This is a required facet on the component.");
    }
    // TODO: put this back in
    //out.println(" * ");
    //out.println(" * @return  the " + facetName + " facet");
    out.println(" */");

    out.println("final public UIComponent " + getMethod + "()");
    out.println("{");
    out.indent();
    out.println("return getFacet(" + facetKey + ");");
    out.unindent();
    out.println("}");
  }

  @Override
  public void writeListenerMethods(
      PrettyWriter out,
      ComponentBean component) throws IOException
  {
    Iterator<EventRefBean> events = component.events();
    while (events.hasNext())
    {
      EventRefBean eventRef = events.next();
      EventBean event = eventRef.resolveEventType();
      if (event != null)
      {
        writeListenerAdd(out, event);
        writeListenerRemove(out, event);
        writeListenersGet(out, event);
      }
    }
  }

  public void writeListenerAdd(
      PrettyWriter out,
      EventBean event) throws IOException
  {
    String listenerFullClass = event.getEventListenerClass();
    String listenerClass = Util.getClassFromFullClass(listenerFullClass);

    String eventName = event.getEventName();
    String addMethod = Util.getMethodNameFromEvent("add", eventName, "Listener");

    out.println();
    out.println("/**");
    out.println(" * Adds a " + eventName + " listener.");
    out.println(" *");
    out.println(" * @param listener  the " + eventName + " listener to add");
    out.println(" */");

    if (isAccessorMethodFinal())
    {
      out.print("final ");
    }
    out.println("public void " + addMethod + "(");
    out.indent();
    out.println(listenerClass + " listener)");
    out.unindent();
    out.println("{");
    out.indent();
    out.println("addFacesListener(listener);");
    out.unindent();
    out.println("}");
  }

  public void writeListenerRemove(
      PrettyWriter out,
      EventBean event) throws IOException
  {
    String listenerFullClass = event.getEventListenerClass();
    String listenerClass = Util.getClassFromFullClass(listenerFullClass);

    String eventName = event.getEventName();
    String removeMethod = Util.getMethodNameFromEvent("remove", eventName, "Listener");

    out.println();
    out.println("/**");
    out.println(" * Removes a " + eventName + " listener.");
    out.println(" *");
    out.println(" * @param listener  the " + eventName + " listener to remove");
    out.println(" */");

    if (isAccessorMethodFinal())
    {
      out.print("final ");
    }
    out.println("public void " + removeMethod + "(");
    out.indent();
    out.println(listenerClass + " listener)");
    out.unindent();
    out.println("{");
    out.indent();
    out.println("removeFacesListener(listener);");
    out.unindent();
    out.println("}");
  }

  public void writeListenersGet(
      PrettyWriter out,
      EventBean event) throws IOException
  {
    String listenerFullClass = event.getEventListenerClass();
    String listenerClass = Util.getClassFromFullClass(listenerFullClass);

    String eventName = event.getEventName();
    String getMethod = Util.getMethodNameFromEvent("get", eventName, "Listeners");

    out.println();
    out.println("/**");
    out.println(" * Returns an array of attached " + eventName + " listeners.");
    out.println(" *");
    out.println(" * @return  an array of attached " + eventName + " listeners.");
    out.println(" */");

    if (isAccessorMethodFinal())
    {
      out.print("final ");
    }
    out.println("public " + listenerClass + "[] " + getMethod + "()");
    out.println("{");
    out.indent();
    out.println("return (" + listenerClass + "[])" +
        "getFacesListeners(" + listenerClass + ".class);");
    out.unindent();
    out.println("}");
  }

  public abstract void writeStateManagementMethods(PrettyWriter out,
                                                   ComponentBean component) throws IOException;

  @Override
  public void writeClientBehaviorMethods(
    PrettyWriter  out,
    ComponentBean component
    ) throws IOException
  {
    String defaultEventName = component.getDefaultEventName();
    out.println();
    out.println("@Override");
    out.println("public String getDefaultEventName()");
    out.println("{");
    out.indent();
    if (defaultEventName != null)
    {
      out.print("return \"");
      out.print(defaultEventName);
      out.println("\";");
    }
    else
    {
      out.println("return super.getDefaultEventName();");
    }
    out.unindent();
    out.println("}");

    out.println();
    out.println("@Override"); // JDK 1.6 is a requirement for JSF2 so this is okay
    out.println("public Collection<String> getEventNames()");
    out.println("{");
    out.indent();
    out.println("return _EVENT_NAMES;");
    out.unindent();
    out.println("}");

    out.println();
    out.println("@Override");
    out.println("public Map<String, List<ClientBehavior>> getClientBehaviors()");
    out.println("{");
    out.indent();
    out.println("return super.getClientBehaviors();");
    out.unindent();
    out.println("}");

    out.println();
    out.println("@Override");
    out.println("public void addClientBehavior(");
    out.indent();
    out.println("String         eventName,");
    out.println("ClientBehavior behavior)");
    out.unindent();
    out.println("{");
    out.indent();
    out.println("super.addClientBehavior(eventName, behavior);");
    out.unindent();
    out.println("}");
  }

  @Override
  public void writeClientBehaviorConstants(
    PrettyWriter  out,
    ComponentBean component
    ) throws IOException
  {
    out.println("// Supported client events for client behaviors:");
    out.println("private final static Collection<String> _EVENT_NAMES = Collections.unmodifiableCollection(");
    out.indent();
    out.println("Arrays.asList(");
    out.indent();
    boolean first = true;
    int wrapAt = 5;
    for (String eventName : component.getEventNames())
    {
      if (first)
      {
        first = false;
      }
      else
      {
        if (--wrapAt < 0)
        {
          out.println(",");
          wrapAt = 5;
        }
        else
        {
          out.print(", ");
        }
      }
      out.print("\"");
      out.print(eventName);
      out.print("\"");
    }
    out.println();
    out.unindent();
    out.println("));");
    out.unindent();
  }

  public void writeOther(
      PrettyWriter out, ComponentBean component, String overrideClassName) throws IOException
  {
    // nothing
  }

  protected String convertMultilineComment(
      String commentBody)
  {
   StringBuilder buff = new StringBuilder(commentBody.replaceAll("\n", "\n * "));


   // escape markup within <pre> blocks.  The tag doc gen plugin escapes the ampersand
   // making it not possible to escape causing issue with javadoc.
   int s = 0;
   do {
     s = buff.indexOf("<pre>", s);
     if (s > 0)
     {
       s = s + "<pre>".length();
       int e = buff.indexOf("</pre>", s);
       e = e - "</pre>".length();
       String markup = buff.substring(s, e);
       markup = markup.replaceAll("<", "&lt;");
       markup = markup.replaceAll(">", "&gt;");
       buff.delete(s, e);
       buff.insert(s, markup);

       s = buff.indexOf("<pre>", s + markup.length() + "</pre>".length());
     }
   } while (s > 0);

   return buff.toString();
  }

  protected class ResolvableTypeFilter implements Filter<PropertyBean>
  {
    @Override
    public boolean accept(PropertyBean property)
    {
      String propertyClass = property.getPropertyClass();
      String resolvableType = resolveType(propertyClass);
      return (resolvableType != null);
    }
  }

  protected class NonVirtualFilter implements Filter<PropertyBean>
  {
    @Override
    public boolean accept(PropertyBean property)
    {
      return (!property.isVirtual());
    }
  }

  protected static class NonOverriddenFilter implements Filter<PropertyBean>
  {
    @Override
    public boolean accept(PropertyBean property)
    {
      return (!property.isOverride());
    }
  }

  static protected String resolveType(
      String className)
  {
    return (String) _RESOLVABLE_TYPES.get(className);
  }

  static private Map _createResolvableTypes()
  {
    Map<String, String> resolvableTypes = new HashMap<String, String>();

    resolvableTypes.put("boolean", "Boolean");
    resolvableTypes.put("char", "Character");
    // TODO: put this back in
    //resolvableTypes.put("java.util.Date", "Date");
    resolvableTypes.put("int", "Integer");
    resolvableTypes.put("float", "Float");
    resolvableTypes.put("double", "Double");
    resolvableTypes.put("java.util.Locale", "Locale");
    resolvableTypes.put("long", "Long");
    resolvableTypes.put("java.lang.String", "String");
    resolvableTypes.put("java.lang.Number", "Number");
    // TODO: put this back in
    //resolvableTypes.put("java.lang.String[]", "StringArray");
    resolvableTypes.put("java.util.TimeZone", "TimeZone");

    return Collections.unmodifiableMap(resolvableTypes);
  }

  static private final Pattern _GENERIC_TYPE = Pattern.compile("([^<]+)<(.+)>");
  static final private Map _RESOLVABLE_TYPES = _createResolvableTypes();
}
