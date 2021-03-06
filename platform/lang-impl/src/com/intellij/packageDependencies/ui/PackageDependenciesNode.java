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

package com.intellij.packageDependencies.ui;

import com.intellij.analysis.AnalysisScopeBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.MutableTreeNode;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class PackageDependenciesNode extends DefaultMutableTreeNode implements Navigatable{
  private static final EmptyIcon EMPTY_ICON = new EmptyIcon(0, IconUtil.getEmptyIcon(false).getIconHeight());

  private Set<PsiFile> myRegisteredFiles = null;
  private boolean myHasUnmarked = false;
  private boolean myHasMarked = false;
  private boolean myEquals;

  public void setEquals(final boolean equals) {
    myEquals = equals;
  }

  public boolean isEquals() {
    return myEquals;
  }

  public void fillFiles(Set<PsiFile> set, boolean recursively) {
    for (PsiFile psiFile : getRegisteredFiles()) {
      if (psiFile != null && psiFile.isValid()) {
        set.add(psiFile);
      }
    }
  }

  public void addFile(PsiFile file, boolean isMarked) {
    getRegisteredFiles().add(file);
    updateMarked(!isMarked, isMarked);
  }

  public Icon getOpenIcon() {
    return EMPTY_ICON;
  }

  public Icon getClosedIcon() {
    return EMPTY_ICON;
  }

  public int getWeight() {
    return 0;
  }

  public boolean hasUnmarked() {
    return myHasUnmarked;
  }

  public boolean hasMarked() {
    return myHasMarked;
  }

  @Nullable
  public PsiElement getPsiElement() {
    return null;
  }

  public FileStatus getStatus(){
    return FileStatus.NOT_CHANGED;
  }

  public int getContainingFiles(){
    int result = 0;
    for (int i = 0; i < getChildCount(); i++) {
      result += ((PackageDependenciesNode)getChildAt(i)).getContainingFiles();
    }
    return result;
  }

  public String getPresentableFilesCount(){
    final int filesCount = getContainingFiles();
    return filesCount > 0 ? " (" + AnalysisScopeBundle.message("package.dependencies.node.items.count", filesCount) + ")" : "";
  }

  public void add(MutableTreeNode newChild) {
    super.add(newChild);
    boolean hasUnmarked = ((PackageDependenciesNode)newChild).hasUnmarked();
    boolean hasMarked = ((PackageDependenciesNode)newChild).hasMarked();
    updateMarked(hasUnmarked, hasMarked);
  }

  private void updateMarked(boolean hasUnmarked, boolean hasMarked) {
    if (hasUnmarked && !myHasUnmarked || hasMarked && !myHasMarked) {
      myHasUnmarked |= hasUnmarked;
      myHasMarked |= hasMarked;
      PackageDependenciesNode parent = ((PackageDependenciesNode)getParent());
      if (parent != null) {
        parent.updateMarked(myHasUnmarked, myHasMarked);
      }
    }
  }

  public void navigate(boolean focus) {
    if (canNavigate()) {
      openTextEditor(focus);
    }
  }

  @Nullable
  private Editor openTextEditor(boolean focus) {
    final OpenFileDescriptor descriptor = getDescriptor();
    if (descriptor != null) {
      return FileEditorManager.getInstance(getProject()).openTextEditor(descriptor, focus);
    }
    return null;
  }

  public boolean canNavigate() {
    if (getProject() == null) return false;
    final PsiElement psiElement = getPsiElement();
    if (psiElement == null) return false;
    final VirtualFile virtualFile = psiElement.getContainingFile().getVirtualFile();
    return virtualFile != null && virtualFile.isValid();
  }

  public boolean canNavigateToSource() {
    return canNavigate();
  }

  @Nullable
  private Project getProject(){
    final PsiElement psiElement = getPsiElement();
    if (psiElement == null || psiElement.getContainingFile() == null){
      return null;
    }
    return psiElement.getContainingFile().getProject();
  }

  @Nullable
  private OpenFileDescriptor getDescriptor() {
    if (getProject() == null) return null;
    final PsiElement psiElement = getPsiElement();
    if (psiElement == null) return null;
    final VirtualFile virtualFile = psiElement.getContainingFile().getVirtualFile();
    if (virtualFile == null || !virtualFile.isValid()) return null;
    return new OpenFileDescriptor(getProject(), virtualFile);
  }

  public Object getUserObject() {
    return toString();
  }

  public boolean isValid() {
    return true;
  }

  public Set<PsiFile> getRegisteredFiles() {
    if (myRegisteredFiles == null) {
      myRegisteredFiles = new HashSet<PsiFile>();
    }
    return myRegisteredFiles;
  }

  @Nullable
  public String getComment() {
    return null;
  }

  public boolean canSelectInLeftTree(Map<PsiFile, Set<PsiFile>> deps){
    return false;
  }
}