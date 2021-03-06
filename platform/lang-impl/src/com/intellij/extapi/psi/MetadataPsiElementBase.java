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

package com.intellij.extapi.psi;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public abstract class MetadataPsiElementBase extends PsiElementBase {

  private final PsiElement mySourceElement;

  public MetadataPsiElementBase(PsiElement sourceElement) {
    mySourceElement = sourceElement;
  }

  public TextRange getTextRange() {
    return mySourceElement != null? mySourceElement.getTextRange() : null;
  }

  public int getStartOffsetInParent() {
    final PsiElement parent = getParent();
    return (parent == null) ? 0 : getTextRange().getStartOffset() - parent.getTextRange().getStartOffset();
  }

  public int getTextLength() {
    return mySourceElement.getTextLength();
  }

  public int getTextOffset() {
    return mySourceElement.getTextOffset();
  }

  public String getText() {
    return mySourceElement.getText();
  }

  @NotNull
  public char[] textToCharArray() {
    return mySourceElement.textToCharArray();
  }

  public boolean textContains(char c) {
    return mySourceElement.textContains(c);
  }

  public PsiElement getSourceElement() {
    return mySourceElement;
  }
}
