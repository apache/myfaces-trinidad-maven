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

/**
 * Private internal enum to 
 * reflect the underlying JSF version.
 */
enum JsfVersion
{
  JSF_1_1,
  JSF_1_2,
  JSF_2_0,
  JSF_2_1;
  
  /**
   * Helper function to extract the used JSF version.
   */
  static JsfVersion getVersion(String jsfVersion)
  {
    if(isJSF12(jsfVersion))
    {
      return JsfVersion.JSF_1_2;  
    }
    else if (isJSF20(jsfVersion))
    {
      return JsfVersion.JSF_2_0;  
    }
    else if (isJSF21(jsfVersion))
    {
      return JsfVersion.JSF_2_1;  
    }
    else
    {
      return JsfVersion.JSF_1_1;  
    }
  }
  
  static boolean isJSF11(String jsfVersion)
  {
    return ("1.1".equals(jsfVersion) || "11".equals(jsfVersion));
  }
  static boolean isJSF12(String jsfVersion)
  {
    return ("1.2".equals(jsfVersion) || "12".equals(jsfVersion));
  }
  static boolean isJSF20(String jsfVersion)
  {
    return ("2.0".equals(jsfVersion) || "20".equals(jsfVersion));
  }
  static boolean isJSF21(String jsfVersion)
  {
    return ("2.1".equals(jsfVersion) || "21".equals(jsfVersion));
  }
}
