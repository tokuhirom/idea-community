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
package com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch;

import com.intellij.cvsSupport2.connections.CvsRootProvider;
import com.intellij.cvsSupport2.cvsoperations.common.CvsExecutionEnvironment;
import com.intellij.cvsSupport2.cvsoperations.common.CvsOperationOnFiles;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import org.netbeans.lib.cvsclient.command.Command;
import org.netbeans.lib.cvsclient.command.tag.TagCommand;

/**
 * author: lesya
 */
public class TagOperation extends CvsOperationOnFiles{
  private final String myTag;
  private final boolean myRemoveTag;
  private final boolean myOverrideExisting;

  public TagOperation(VirtualFile[] files, String tag, boolean removeTag, boolean overrideExisting) {
    for (VirtualFile file : files) {
      addFile(file);
    }
    myRemoveTag = removeTag;
    myTag = tag;
    myOverrideExisting = overrideExisting;
  }

  public TagOperation(FilePath[] files, String tag, boolean overrideExisting) {
    for (FilePath file : files) {
      addFile(file.getIOFile());
    }
    myRemoveTag = false;
    myTag = tag;
    myOverrideExisting = overrideExisting;
  }

  protected Command createCommand(CvsRootProvider root, CvsExecutionEnvironment cvsExecutionEnvironment) {
    TagCommand tagCommand = new TagCommand();
    addFilesToCommand(root, tagCommand);
    tagCommand.setTag(myTag);
    tagCommand.setDeleteTag(myRemoveTag);
    tagCommand.setOverrideExistingTag(myOverrideExisting);
    return tagCommand;
  }

  protected String getOperationName() {
    return "tag";
  }
}
