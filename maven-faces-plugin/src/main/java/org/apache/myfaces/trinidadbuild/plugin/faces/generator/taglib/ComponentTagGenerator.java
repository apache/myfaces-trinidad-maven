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

import org.apache.myfaces.trinidadbuild.plugin.faces.generator.ClassGenerator;
import org.apache.myfaces.trinidadbuild.plugin.faces.io.PrettyWriter;
import org.apache.myfaces.trinidadbuild.plugin.faces.parse.ComponentBean;

import java.io.IOException;
import java.util.Collection;

/**
 * Generates tag classes
 *
 * @author Bruno Aranda (latest modification by $Author$)
 * @version $Revision$ $Date$
 */
public interface ComponentTagGenerator extends ClassGenerator
{

  void writeSetPropertiesMethod(PrettyWriter out,
                                String componentClass,
                                ComponentBean component) throws IOException;

  void writeSetPropertiesMethod(PrettyWriter out,
                                String componentClass,
                                Collection<ComponentBean> components) throws IOException;

  void writeReleaseMethod(PrettyWriter out,
                          ComponentBean component) throws IOException;

  void writeReleaseMethod(PrettyWriter out,
                          Collection<ComponentBean> components) throws IOException;

  void writeGetComponentType(PrettyWriter out,
                             ComponentBean component) throws IOException;

  void writeGetRendererType(PrettyWriter out,
                            ComponentBean component) throws IOException;

  void writePropertyMembers(PrettyWriter out,
                            ComponentBean component) throws IOException;

  void writePropertyMembers(PrettyWriter out,
                            Collection<ComponentBean> components) throws IOException;
}
