/*
 * Copyright 2007-2008 Dave Griffith
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
package org.jetbrains.plugins.groovy.codeInspection.assignment;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForInClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;

public class GroovyAssignmentToMethodParameterInspection extends BaseInspection {

  @Nls
  @NotNull
  public String getGroupDisplayName() {
    return ASSIGNMENT_ISSUES;
  }

  @Nls
  @NotNull
  public String getDisplayName() {
    return "Assignment to method parameter";
  }

  @Nullable
  protected String buildErrorString(Object... args) {
    return "Assignment to method parameter '#ref' #loc";

  }

  public BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  private static class Visitor extends BaseInspectionVisitor {
    
    public void visitAssignmentExpression(GrAssignmentExpression grAssignmentExpression) {
      super.visitAssignmentExpression(grAssignmentExpression);
      final GrExpression lhs = grAssignmentExpression.getLValue();
      if (!(lhs instanceof GrReferenceExpression)) {
        return;
      }
      final PsiElement referent = ((PsiReference) lhs).resolve();
      if (referent == null) {
        return;
      }
      if (!(referent instanceof GrParameter)) {
        return;
      }
      if (referent.getParent() instanceof GrForInClause) {
        return;
      }
      registerError(lhs);
    }
  }
}