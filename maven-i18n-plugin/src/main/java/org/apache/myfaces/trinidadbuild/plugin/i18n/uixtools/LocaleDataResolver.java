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
package org.apache.myfaces.trinidadbuild.plugin.i18n.uixtools;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import java.util.Enumeration;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Resolves locale specific elements like the names for month or date formats.
 * With Java 6 the needed resources are not stored anymore in reousrce files
 * but are hardcoded and can be accessed thru sun.util.resources.LocaleData.
 * <br>
 * This class uses reflection to access the resources to be compatible with
 * Java 1.4, 5 and 6.
 */
class LocaleDataResolver
{

  private static final String LOCAL_ELEMENTS_RESOURCE_BUNDLE_JAVA_5 = "sun.text.resources.LocaleElements";

  private static final String LOCAL_DATA_CLASS_JAVA_4 = "sun.text.resources.LocaleData";

  private static final String LOCAL_DATA_CLASS_JAVA_6 = "sun.util.resources.LocaleData";

  private static final String[] LOCAL_DATA_METHODS_JAVA_6 = new String[]
  {
    "getCalendarData", "getCollationData", "getCurrencyNames", "getDateFormatData", "getLocaleNames",
    "getNumberFormatData", "getTimeZoneNames"
  };

  private static final String DATE_TIME_ELEMENTS = "DateTimeElements";

  private static final String MINIMAL_DAYS_IN_FIRST_WEEK = "minimalDaysInFirstWeek";

  private static final String FIRST_DAY_OF_WEEK = "firstDayOfWeek";

  /**
   * Returns the element data for the given key and locale.
   *
   * @param key the key for the element
   * @param locale the locale to be used
   * @return the locale dependent element
   */
  public static Object getElementData(String key, Locale locale)
  {
    try
    {
      Class.forName(LOCAL_DATA_CLASS_JAVA_4);
      return _getElementDataJava5(key, locale);
    }
    catch (ClassNotFoundException e)
    {
      try
      {
        Class.forName(LOCAL_DATA_CLASS_JAVA_6);
        return _getElementDataJava6(key, locale);
      }
      catch (ClassNotFoundException e1)
      {
        throw new IllegalStateException("could not access the java resource bundles");
      }
    }
  }

  /**
   * Returns the element data for the given key and locale using
   * the java 1.4/5 mechanism to access the resources.
   *
   * @param key the key for the element
   * @param locale the locale to be used
   * @return the locale dependent element
   */
  private static Object _getElementDataJava5(String key, Locale locale)
  {
    ResourceBundle elementsData = ResourceBundle.getBundle(LOCAL_ELEMENTS_RESOURCE_BUNDLE_JAVA_5, locale);
    return elementsData.getObject(key);
  }

  /**
   * Returns the element data for the given key and locale using
   * the java 6 mechanism to access the resources.
   *
   * @param key the key for the element
   * @param locale the locale to be used
   * @return the locale dependent element
   */
  private static Object _getElementDataJava6(String key, Locale locale)
  {
    if (DATE_TIME_ELEMENTS.equals(key))
    {
      return new Object[]
    {
      _getElementDataJava6(FIRST_DAY_OF_WEEK, locale), _getElementDataJava6(MINIMAL_DAYS_IN_FIRST_WEEK, locale)
    };
    }
    else
    {
      for (int i = 0; i < LOCAL_DATA_METHODS_JAVA_6.length; i++)
      {
        ResourceBundle bundle = _getLocaleDataResourceBundleJava6(LOCAL_DATA_METHODS_JAVA_6[i], locale);
        if (_containsKey(bundle, key))
        {
          return bundle.getObject(key);
        }
      }
    }

    throw new MissingResourceException("no element found in the java resource bundles for the given key", null, key);
  }

  /**
   * @param bundle
   * @param key
   * @return true if the given key exists in the given bundle
   */
  private static boolean _containsKey(ResourceBundle bundle, String key)
  {
    for (Enumeration e = bundle.getKeys(); e.hasMoreElements();)
    {
      if (((String) e.nextElement()).equals(key))
        return true;
    }
    return false;
  }

  /**
   * Gives access to the java 6 implementation using reflection.
   *
   * @param name
   * @param locale
   * @return the resource bundle for the given method name and locale
   */
  private static ResourceBundle _getLocaleDataResourceBundleJava6(String name, Locale locale)
  {
    try
    {
      Class localDataClass = Class.forName(LOCAL_DATA_CLASS_JAVA_6);
      Method method = localDataClass.getMethod(name, new Class[]
      {
        Locale.class
      });
      Object bundle = method.invoke(null, new Object[]
      {
        locale
      });
      return (ResourceBundle) bundle;
    }
    catch (ClassNotFoundException e)
    {
      throw new IllegalStateException();
    }
    catch (SecurityException e)
    {
      throw new IllegalStateException();
    }
    catch (NoSuchMethodException e)
    {
      throw new IllegalStateException();
    }
    catch (IllegalArgumentException e)
    {
      throw new IllegalStateException();
    }
    catch (IllegalAccessException e)
    {
      throw new IllegalStateException();
    }
    catch (InvocationTargetException e)
    {
      throw new IllegalStateException();
    }
  }
}
