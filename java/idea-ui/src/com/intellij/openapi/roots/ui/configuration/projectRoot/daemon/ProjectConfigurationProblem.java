/*
 * Copyright 2000-2010 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.roots.ui.configuration.projectRoot.daemon;

import com.intellij.openapi.roots.ui.configuration.ConfigurationError;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

/**
* @author nik
*/
class ProjectConfigurationProblem extends ConfigurationError {
  private final StructureConfigurableContext myContext;
  private final ProjectStructureProblemDescription myDescription;

  public ProjectConfigurationProblem(StructureConfigurableContext context, ProjectStructureProblemDescription description) {
    super(description.getMessage(), description.getDescription() != null ? description.getDescription() : description.getMessage());
    myContext = context;
    myDescription = description;
  }

  @Override
  public void navigate() {
    ProjectStructureConfigurable.getInstance(myContext.getProject()).navigateTo(myDescription.getPlace(), true);
  }

  @Override
  public boolean canBeFixed() {
    return !myDescription.getFixes().isEmpty();
  }

  @Override
  public void fix(JComponent contextComponent) {
    final List<ConfigurationErrorQuickFix> fixes = myDescription.getFixes();
    if (fixes.size() == 1) {
      fixes.get(0).performFix();
    }
    else {
      JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<ConfigurationErrorQuickFix>(null, fixes) {
        @NotNull
        @Override
        public String getTextFor(ConfigurationErrorQuickFix value) {
          return value.getActionName();
        }

        @Override
        public PopupStep onChosen(ConfigurationErrorQuickFix selectedValue, boolean finalChoice) {
          selectedValue.performFix();
          return FINAL_CHOICE;
        }
      }).showUnderneathOf(contextComponent);
    }
  }
}
