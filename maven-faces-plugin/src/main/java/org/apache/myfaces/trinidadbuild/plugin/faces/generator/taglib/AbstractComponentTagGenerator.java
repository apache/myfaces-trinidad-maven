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

import java.io.IOException;

import java.lang.reflect.Modifier;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import org.apache.myfaces.trinidadbuild.plugin.faces.generator.GeneratorHelper;
import org.apache.myfaces.trinidadbuild.plugin.faces.io.PrettyWriter;
import org.apache.myfaces.trinidadbuild.plugin.faces.parse.ComponentBean;
import org.apache.myfaces.trinidadbuild.plugin.faces.parse.PropertyBean;

import org.apache.myfaces.trinidadbuild.plugin.faces.util.Filter;
import org.apache.myfaces.trinidadbuild.plugin.faces.util.FilteredIterator;
import org.apache.myfaces.trinidadbuild.plugin.faces.util.SourceTemplate;
import org.apache.myfaces.trinidadbuild.plugin.faces.util.Util;


/**
 * TODO: comment this!
 *
 * @author Bruno Aranda (latest modification by $Author$)
 * @version $Revision$ $Date$
 */
public abstract class AbstractComponentTagGenerator implements ComponentTagGenerator
{
  @Override
  public void writeImports(PrettyWriter out,
                           SourceTemplate template,
                           String packageName,
                           String fullSuperclassName,
                           String superclassName,
                           ComponentBean component)
  {
    Collection<ComponentBean> components = new HashSet<ComponentBean>();
    components.add(component);
    writeImports(out, template, packageName, fullSuperclassName, superclassName, components);
  }


  @Override
  public void writeImports(PrettyWriter out, SourceTemplate template, String packageName,
    String fullSuperclassName, String superclassName, Collection<ComponentBean> components)
  {
    // TODO: support SourceTemplate

    Set<String> imports = new TreeSet<String>();

    for (Iterator<ComponentBean> lIterator = components.iterator(); lIterator.hasNext();)
    {
      ComponentBean component = lIterator.next();
      Iterator<PropertyBean> properties = component.properties();
      properties = new FilteredIterator<PropertyBean>(properties, new TagAttributeFilter());

      // TODO: remove these imports
      // FIXME: Actually last 2 can be kept when not abstract
      //imports.add("javax.faces.component.UIComponent");

      // superclassName is fully qualified if it collides
      // with the generated class name and should not be
      // imported when such a collision would occur
      if (!superclassName.equals(fullSuperclassName))
      {
        imports.add(fullSuperclassName);
      }

      while (properties.hasNext())
      {
        PropertyBean property = properties.next();

        String propertyClass = property.getPropertyClass();

        if (propertyClass != null && property.isLiteralOnly())
        {
          // Import the property class only if only litterals are supported
          // otherwise the class will be a String inside the tag to support
          // ValueBinding
          imports.add(propertyClass);
        }

        // TODO: restore import and make reference to
        //       ConstantMethodBinding relative rather
        //       than absolute
        //if (property.isMethodBinding() &&
        //    isStringMethodBindingReturnType(property))
        //{
        //  imports.add("org.apache.myfaces.trinidadinternal.taglib.ConstantMethodBinding");
        //}
      }

      addSpecificImports(imports, component);

    }
    // do not import implicit!
    imports.removeAll(Util.PRIMITIVE_TYPES);

    GeneratorHelper.writeImports(out, packageName, imports);
  }

  @Override
  public void writeClassBegin(PrettyWriter out,
                              String className,
                              String superclassName,
                              ComponentBean component,
                              SourceTemplate template,
                              boolean hasTemplate)
  {
    // TODO: add support for source template

    int modifiers = component.getTagClassModifiers();

    // If there is no source template but there is a sub-class, then make the generate class
    // abstract.
    if (template == null && hasTemplate)
    {
      modifiers |= Modifier.ABSTRACT;
    }

    String classStart = Modifier.toString(modifiers);

    out.println("/**");
    out.println(" * Auto-generated tag class.");
    out.println(" */");

    // TODO: use canonical ordering
    classStart = classStart.replaceAll("public abstract", "abstract public");
    out.println(classStart + " class " + className +
        " extends " + superclassName);
    out.println("{");
    out.indent();
  }


  @Override
  public void writeConstructor(PrettyWriter out,
                               ComponentBean component,
                               String overrideClassName,
                               int modifiers) throws IOException
  {
    String className;

    if (overrideClassName != null)
    {
      className = overrideClassName;
    }
    else
    {
      String fullClassName = component.getTagClass();
      className = Util.getClassFromFullClass(fullClassName);
    }

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

  @Override
  public void writeGetComponentType(
      PrettyWriter out,
      ComponentBean component) throws IOException
  {
    String componentType = component.getComponentType();
    out.println();

    // The superclass does not necessarily need to have this method
    //out.println("@Override");
    out.println("public String getComponentType()");
    out.println("{");
    out.indent();
    out.println("return \"" + componentType + "\";");
    out.unindent();
    out.println("}");
  }

  @Override
  public void writeGetRendererType(
      PrettyWriter out,
      ComponentBean component) throws IOException
  {
    String rendererType = component.getRendererType();
    out.println();

    // The superclass does not necessarily need to have this method
    //out.println("@Override");
    out.println("public String getRendererType()");
    out.println("{");
    out.indent();
    out.println("return " + Util.convertStringToLiteral(rendererType) + ";");
    out.unindent();
    out.println("}");
  }

  @Override
  public void writeClassEnd(PrettyWriter out)
  {
    out.unindent();
    out.println("}");
  }

  public void writePropertyMembers(PrettyWriter out,
                                   ComponentBean component) throws IOException
  {
    Iterator<PropertyBean> properties = component.properties();
    properties = new FilteredIterator<PropertyBean>(properties, new TagAttributeFilter());
    if (isSetterMethodFinal())
    {
      // Do not generate property methods if they are final and the properties are overrides
      properties = new FilteredIterator<PropertyBean>(properties, new NonOverriddenFilter());
    }

    while (properties.hasNext())
    {
      PropertyBean property = properties.next();
      writePropertyDeclaration(out, property);
      writePropertySetter(out, property);
    }
  }

  /**
   * Whether the tag setter methods have the final modifier
   *
   * @return true if the setter methods are final
   */
  protected boolean isSetterMethodFinal()
  {
    return false;
  }

  @Override
  public void writePropertyMembers(PrettyWriter out, Collection<ComponentBean> components) throws IOException
  {
    for (Iterator<ComponentBean> lIterator = components.iterator(); lIterator.hasNext();)
    {
      writePropertyMembers(out, lIterator.next());
    }
  }

  @Override
  public void writeReleaseMethod(PrettyWriter out,
                                 ComponentBean component) throws IOException
  {
    Collection<ComponentBean> components = new HashSet<ComponentBean>();
    components.add(component);
    writeReleaseMethod(out, components);
  }


  @Override
  public void writeReleaseMethod(
    PrettyWriter out, Collection<ComponentBean> components) throws IOException
  {
    Collection<PropertyBean> all = new HashSet<PropertyBean>();
    boolean special = false;
    for (Iterator<ComponentBean> lIterator = components.iterator(); lIterator.hasNext();)
    {
      ComponentBean component = lIterator.next();
      Iterator<PropertyBean> prop = component.properties();
      // TODO: remove special case for UIXFormTag
      special |= "org.apache.myfaces.trinidadinternal.taglib.UIXFormTag".equals(component.getTagClass());
      while (prop.hasNext())
      {
        all.add(prop.next());
      }
    }

    Iterator<PropertyBean> properties = all.iterator();
    properties = new FilteredIterator<PropertyBean>(properties, new TagAttributeFilter());
    properties = new FilteredIterator<PropertyBean>(properties, new NonOverriddenFilter());

    if (properties.hasNext() || special)
    {
      out.println();
      out.println("@Override");
      out.println("public void release()");
      out.println("{");
      out.indent();
      out.println("super.release();");
      while (properties.hasNext())
      {
        PropertyBean property = properties.next();
        String propName = property.getPropertyName();
        String propVar = "_" + propName;
        out.print(propVar + " = ");
        String type = GeneratorHelper.getJspPropertyType(property, is12());
        // FIXME: support all primitive types.  In practice, the only types
        // that have come up are ValueExpression, String, and boolean
        if ("boolean".equals(type))
          out.println("false;");
        else
          out.println("null;");
      }
      out.unindent();
      out.println("}");
    }
  }

  protected void addSpecificImports(@SuppressWarnings("unused") Set<String> imports,
                                    @SuppressWarnings("unused") ComponentBean component)
  {
    // nothing by default
  }

  /**
   * Returns true if the method is being used for generating JSF 1.2 code.
   */
  protected abstract boolean is12();

  protected abstract void writePropertyDeclaration(PrettyWriter out,
                                                   PropertyBean property) throws IOException;

  protected abstract void writePropertySetter(PrettyWriter out,
                                              PropertyBean property) throws IOException;

  protected abstract void writeSetPropertyMethodBody(PrettyWriter out,
                                                     String componentClass,
                                                  Iterator<PropertyBean> properties) throws IOException;

  protected static class NonOverriddenFilter implements Filter<PropertyBean>
  {
    @Override
    public boolean accept(
      PropertyBean property)
    {
      return (!property.isOverride());
    }
  }
}
