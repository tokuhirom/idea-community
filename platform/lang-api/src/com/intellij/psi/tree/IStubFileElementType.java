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

package com.intellij.psi.tree;

import com.intellij.lang.Language;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.StubBuilder;
import com.intellij.psi.stubs.*;
import org.jetbrains.annotations.NonNls;

import java.io.IOException;

/*
 * @author max
 */
public class IStubFileElementType<T extends PsiFileStub> extends IFileElementType implements StubSerializer<T> {
  public IStubFileElementType(final Language language) {
    super(language);
  }

  public IStubFileElementType(@NonNls final String debugName, final Language language) {
    super(debugName, language);
  }

  public int getStubVersion() {
    return 0;
  }

  public StubBuilder getBuilder() {
    return new DefaultStubBuilder();
  }

  public String getExternalId() {
    return "psi.file";
  }

  public void serialize(final T stub, final StubOutputStream dataStream) throws IOException {
  }

  public T deserialize(final StubInputStream dataStream, final StubElement parentStub) throws IOException {
    return (T)new PsiFileStubImpl(null);
  }

  public void indexStub(final PsiFileStub stub, final IndexSink sink) {
  }

  public boolean shouldBuildStubFor(final VirtualFile file) {
    return true;
  }
}