/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.util.indexing;

import java.util.Iterator;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 14, 2007
 */
public abstract class ValueContainer<Value> {
  interface IntIterator {
    boolean hasNext();
    
    int next();

    int size();
  }
  
  public abstract IntIterator getInputIdsIterator(Value value);

  public abstract boolean isAssociated(Value value, int inputId);
  
  public abstract Iterator<Value> getValueIterator();

  public abstract int[] getInputIds(Value value);

  public abstract List<Value> toValueList();

  public abstract int size();


  public interface ContainerAction<T> {
    void perform(int id, T value);
  }

  public final void forEach(final ContainerAction<Value> action) {
    for (final Iterator<Value> valueIterator = getValueIterator(); valueIterator.hasNext();) {
      final Value value = valueIterator.next();
      for (final IntIterator intIterator = getInputIdsIterator(value); intIterator.hasNext();) {
        action.perform(intIterator.next(), value);
      }
    }
  }

  private volatile boolean myNeedsCompacting = false;

  boolean needsCompacting() {
    return myNeedsCompacting;
  }

  void setNeedsCompacting(boolean value) {
    myNeedsCompacting = value;
  }
}
