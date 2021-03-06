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
package org.jetbrains.plugins.groovy.gant;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.sun.jdi.ReferenceType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.extensions.GroovyScriptTypeDetector;
import org.jetbrains.plugins.groovy.extensions.debugger.ScriptPositionManagerHelper;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

/**
 * @author ilyas
 */
public class GantPositionManagerHelper extends ScriptPositionManagerHelper {

  public boolean isAppropriateRuntimeName(@NotNull final String runtimeName) {
    return true;
  }

  public boolean isAppropriateScriptFile(@NotNull final PsiFile scriptFile) {
    return GroovyScriptTypeDetector.isSpecificScriptFile(scriptFile, GantScriptType.INSTANCE);
  }

  @NotNull
  public String getRuntimeScriptName(@NotNull final String originalName, GroovyFile groovyFile) {
    return originalName;
  }

  public PsiFile getExtraScriptIfNotFound(ReferenceType refType, @NotNull final String runtimeName, final Project project) {
    try {
      PsiFile[] files = FilenameIndex.getFilesByName(project, runtimeName + "." + GantScriptType.DEFAULT_EXTENSION,
                                                     GlobalSearchScope.allScope(project));
      if (files.length == 1) return files[0];

      if (files.length == 0) {
        files = FilenameIndex.getFilesByName(project, runtimeName + ".groovy", GlobalSearchScope.allScope(project));

        PsiFile candidate = null;
        for (PsiFile file : files) {
          if (GroovyScriptTypeDetector.isSpecificScriptFile(file, GantScriptType.INSTANCE)) {
            if (candidate != null) return null;
            candidate = file;
          }
        }

        return candidate;
      }
    }
    catch (ProcessCanceledException ignored) {
    }
    catch (IndexNotReadyException ignored) {
    }
    return null;
  }
}
