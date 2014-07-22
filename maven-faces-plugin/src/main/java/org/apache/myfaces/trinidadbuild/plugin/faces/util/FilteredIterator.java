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

import java.util.Iterator;
import java.util.NoSuchElementException;

public class FilteredIterator<T> implements Iterator<T>
{
  public FilteredIterator(
    Iterator<T> iter,
    Filter<T>   filter)
  {
    _iter = iter;
    _filter = filter;
    _advance();
  }

  @Override
  public boolean hasNext()
  {
    return (_next != null);
  }

  @Override
  public T next()
  {
    if (_next == null)
      throw new NoSuchElementException();

    T obj = _next;
    _advance();
    return obj;
  }

  @Override
  public void remove()
  {
    throw new UnsupportedOperationException();
  }

  private void _advance()
  {
    while (_iter.hasNext())
    {
      T obj = _iter.next();
      if (_filter.accept(obj))
      {
        _next = obj;
        return;
      }
    }

    _next = null;
  }

  private final Iterator<T> _iter;
  private final Filter<T> _filter;
  private T _next;
}
