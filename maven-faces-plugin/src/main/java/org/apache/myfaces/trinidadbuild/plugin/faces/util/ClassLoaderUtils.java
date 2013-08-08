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
package org.apache.myfaces.trinidadbuild.plugin.faces.util;


/**
 * Utility methods for accessing classes and resources using an appropriate
 * class loader.
 *
 */
public final class ClassLoaderUtils
{
  // Utility class only, no instances
  private ClassLoaderUtils()
  {
  }
  
  /**
   * Loads the class with the specified name.  For Java 2 callers, the
   * current thread's context class loader is preferred, falling back on the
   * system class loader of the caller when the current thread's context is not
   * set, or the caller is pre Java 2.
   *
   * @param     name  the name of the class
   * @return    the resulting <code>Class</code> object
   * @exception ClassNotFoundException if the class was not found
   */
  public static Class<?> loadClass(
    String name) throws ClassNotFoundException
  {
    return loadClass(name, null);
  }

  /**
   * Loads the class with the specified name.  For Java 2 callers, the
   * current thread's context class loader is preferred, falling back on the
   * class loader of the caller when the current thread's context is not set,
   * or the caller is pre Java 2.  If the callerClassLoader is null, then
   * fall back on the system class loader.
   *
   * @param     name  the name of the class
   * @param     callerClassLoader  the calling class loader context
   * @return    the resulting <code>Class</code> object
   * @exception ClassNotFoundException if the class was not found
   */
  public static Class<?> loadClass(
    String      name,
    ClassLoader callerClassLoader) throws ClassNotFoundException
  {
    Class<?> clazz = null;

    try
    {
      ClassLoader loader = getContextClassLoader();

      if (loader != null)
        clazz = loader.loadClass(name);
    }
    catch (ClassNotFoundException e)
    {
      // treat as though loader not set
      ;
    }

    if (clazz == null)
    {
      if (callerClassLoader != null)
        clazz = callerClassLoader.loadClass(name);
      else
        clazz = Class.forName(name);
    }

    return clazz;
  }
  
  /**
   * Dynamically accesses the current context class loader.
   * Returns null if there is no per-thread context class loader.
   */
  public static ClassLoader getContextClassLoader()
  {
    return Thread.currentThread().getContextClassLoader();
  }

}
