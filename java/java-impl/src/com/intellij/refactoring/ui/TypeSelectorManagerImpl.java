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
package com.intellij.refactoring.ui;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypeUtil;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.TailType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.statistics.StatisticsInfo;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.util.RefactoringHierarchyUtil;
import com.intellij.util.ArrayUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author dsl
 */
public class TypeSelectorManagerImpl implements TypeSelectorManager {
  private SmartTypePointer myPointer;
  private PsiType myDefaultType;
  private final PsiExpression myMainOccurence;
  private final PsiExpression[] myOccurrences;
  private final PsiType[] myTypesForMain;
  private final PsiType[] myTypesForAll;
  private final boolean myIsOneSuggestion;
  private TypeSelector myTypeSelector;
  private final PsiElementFactory myFactory;
  private final SmartTypePointerManager mySmartTypePointerManager;
  private ExpectedTypesProvider.ExpectedClassProvider myOccurrenceClassProvider;
  private ExpectedTypesProvider myExpectedTypesProvider;

  public TypeSelectorManagerImpl(Project project, PsiType type, PsiExpression mainOccurence, PsiExpression[] occurrences) {
    this(project, type, null, mainOccurence, occurrences);
  }

  public TypeSelectorManagerImpl(Project project, PsiType type, PsiExpression[] occurrences) {
    this(project, type, occurrences, true);
  }

  public TypeSelectorManagerImpl(Project project, PsiType type, PsiExpression[] occurrences, boolean areTypesDirected) {
    myFactory = JavaPsiFacade.getInstance(project).getElementFactory();
    mySmartTypePointerManager = SmartTypePointerManager.getInstance(project);
    setDefaultType(type);
    myMainOccurence = null;
    myOccurrences = occurrences;
    myExpectedTypesProvider = ExpectedTypesProvider.getInstance(project);
    myOccurrenceClassProvider = createOccurrenceClassProvider();
    myTypesForAll = getTypesForAll(areTypesDirected);
    myTypesForMain = PsiType.EMPTY_ARRAY;
    myIsOneSuggestion = myTypesForAll.length == 1;

    if (myIsOneSuggestion) {
      myTypeSelector = new TypeSelector(myTypesForAll[0]);
    }
    else {
      myTypeSelector = new TypeSelector();
      setTypesAndPreselect(myTypesForAll);
    }
  }

  public TypeSelectorManagerImpl(Project project,
                                 PsiType type,
                                 PsiMethod containingMethod,
                                 PsiExpression mainOccurence,
                                 PsiExpression[] occurrences) {
    myFactory = JavaPsiFacade.getInstance(project).getElementFactory();
    mySmartTypePointerManager = SmartTypePointerManager.getInstance(project);
    setDefaultType(type);
    myMainOccurence = mainOccurence;
    myOccurrences = occurrences;
    myExpectedTypesProvider = ExpectedTypesProvider.getInstance(project);

    myOccurrenceClassProvider = createOccurrenceClassProvider();
    myTypesForMain = getTypesForMain();
    myTypesForAll = getTypesForAll(true);

    if (containingMethod != null) {
      if (PsiUtil.resolveClassInType(type) != null) {
        setDefaultType(checkIfTypeAccessible(type, project, containingMethod));
      }
    }

    myIsOneSuggestion =
      myTypesForMain.length == 1 && myTypesForAll.length == 1 &&
      myTypesForAll[0].equals(myTypesForMain[0]);
    if (myIsOneSuggestion) {
      myTypeSelector = new TypeSelector(myTypesForAll[0]);
    }
    else {
      myTypeSelector = new TypeSelector();
    }
  }

  private PsiType checkIfTypeAccessible(PsiType type, Project project, PsiMethod containingMethod) {
    PsiClass parentClass = containingMethod.getContainingClass();
    final PsiClass typeClass = PsiUtil.resolveClassInType(type);
    if (typeClass != null) {
      if (typeClass instanceof PsiTypeParameter) {
        if (ArrayUtil.find(parentClass.getTypeParameters(), typeClass) == -1) { //unknown type parameter
          return PsiType.getJavaLangObject(PsiManager.getInstance(project), GlobalSearchScope.allScope(project));
        }
      } else if (PsiTreeUtil.isAncestor(containingMethod, typeClass, true)) { //local class type
        final int nextTypeIdx = ArrayUtil.find(myTypesForAll, type) + 1;
        if (nextTypeIdx < myTypesForAll.length) {
          return checkIfTypeAccessible(myTypesForAll[nextTypeIdx], project, containingMethod);
        }
      }
    }
    return type;
  }

  public PsiType[] getTypesForAll() {
    return myTypesForAll;
  }

  public PsiType getDefaultType() {
    if (myDefaultType.isValid()) {
      return myDefaultType;
    }
    return myPointer.getType();
  }

  public void setDefaultType(PsiType defaultType) {
    myDefaultType = defaultType;
    myPointer = mySmartTypePointerManager.createSmartTypePointer(defaultType);
  }

  private ExpectedTypesProvider.ExpectedClassProvider createOccurrenceClassProvider() {
    final Set<PsiClass> occurrenceClasses = new HashSet<PsiClass>();
    for (final PsiExpression occurence : myOccurrences) {
      final PsiType occurrenceType = occurence.getType();
      final PsiClass aClass = PsiUtil.resolveClassInType(occurrenceType);
      if (aClass != null) {
        occurrenceClasses.add(aClass);
      }
    }
    return new ExpectedTypeUtil.ExpectedClassesFromSetProvider(occurrenceClasses);
  }

  private PsiType[] getTypesForMain() {
    final ExpectedTypeInfo[] expectedTypes = ExpectedTypesProvider.getExpectedTypes(myMainOccurence, false, myOccurrenceClassProvider,
                                                                                    false);
    final ArrayList<PsiType> allowedTypes = new ArrayList<PsiType>();
    RefactoringHierarchyUtil.processSuperTypes(getDefaultType(), new RefactoringHierarchyUtil.SuperTypeVisitor() {
      public void visitType(PsiType aType) {
        checkIfAllowed(aType);
      }

      public void visitClass(PsiClass aClass) {
        checkIfAllowed(myFactory.createType(aClass));
      }

      private void checkIfAllowed(PsiType type) {
        if (expectedTypes != null && expectedTypes.length > 0) {
          final ExpectedTypeInfo
              typeInfo = ExpectedTypesProvider.createInfo(type, ExpectedTypeInfo.TYPE_STRICTLY, type, TailType.NONE);
          for (ExpectedTypeInfo expectedType : expectedTypes) {
            if (expectedType.intersect(typeInfo).length != 0) {
              allowedTypes.add(type);
              break;
            }
          }
        }
        else {
          allowedTypes.add(type);
        }
      }
    });

    ArrayList<PsiType> result = normalizeTypeList(allowedTypes);
    return result.toArray(new PsiType[result.size()]);
  }

  private PsiType[] getTypesForAll(final boolean areTypesDirected) {
    final ArrayList<ExpectedTypeInfo[]> expectedTypesFromAll = new ArrayList<ExpectedTypeInfo[]>();
    for (PsiExpression occurrence : myOccurrences) {

      final ExpectedTypeInfo[] expectedTypes = ExpectedTypesProvider.getExpectedTypes(occurrence, false, myOccurrenceClassProvider,
                                                                                      isUsedAfter());
      if (expectedTypes.length > 0) {
        expectedTypesFromAll.add(expectedTypes);
      }
    }

    final ArrayList<PsiType> allowedTypes = new ArrayList<PsiType>();
    RefactoringHierarchyUtil.processSuperTypes(getDefaultType(), new RefactoringHierarchyUtil.SuperTypeVisitor() {
      public void visitType(PsiType aType) {
        checkIfAllowed(aType);
      }

      public void visitClass(PsiClass aClass) {
        checkIfAllowed(myFactory.createType(aClass));
      }

      private void checkIfAllowed(PsiType type) {
        NextInfo:
        for (ExpectedTypeInfo[] expectedTypes : expectedTypesFromAll) {
          for (final ExpectedTypeInfo info : expectedTypes) {
            if (ExpectedTypeUtil.matches(type, info)) continue NextInfo;
          }
          return;
        }
        allowedTypes.add(type);
      }
    });

    final ArrayList<PsiType> result = normalizeTypeList(allowedTypes);
    if (!areTypesDirected) {
      Collections.reverse(result);
    }
    return result.toArray(new PsiType[result.size()]);
  }

  protected boolean isUsedAfter() {
    return false;
  }

  private ArrayList<PsiType> normalizeTypeList(final ArrayList<PsiType> typeList) {
    ArrayList<PsiType> result = new ArrayList<PsiType>();
    TypeListCreatingVisitor visitor = new TypeListCreatingVisitor(result, myFactory);
    for (PsiType psiType : typeList) {
      visitor.visitType(psiType);
    }

    final PsiType defaultType = getDefaultType();
    for (int index = 0; index < result.size(); index++) {
      PsiType psiType = result.get(index);
      if (psiType.equals(defaultType)) {
        result.remove(index);
        break;
      }
    }

    final PsiPrimitiveType unboxedType = PsiPrimitiveType.getUnboxedType(defaultType);
    if (unboxedType != null) {
      result.remove(unboxedType);
      result.add(0, unboxedType);
    }

    if (defaultType instanceof PsiPrimitiveType && myMainOccurence != null) {
      final PsiClassType boxedType = ((PsiPrimitiveType)defaultType).getBoxedType(myMainOccurence);
      if (boxedType != null) {
        result.remove(boxedType);
        result.add(0, boxedType);
      }
    }
    result.add(0, defaultType);
    return result;
  }

  public void setAllOccurences(boolean allOccurences) {
    if (myIsOneSuggestion) return;
    setTypesAndPreselect(allOccurences ? myTypesForAll : myTypesForMain);
  }

  private void setTypesAndPreselect(PsiType[] types) {
    myTypeSelector.setTypes(types);

    Map<String, PsiType> map = new THashMap<String, PsiType>();
    for (final PsiType type : types) {
      map.put(serialize(type), type);
    }

    for (StatisticsInfo info : StatisticsManager.getInstance().getAllValues(getStatsKey())) {
      final PsiType candidate = map.get(info.getValue());
      if (candidate != null && StatisticsManager.getInstance().getUseCount(info) > 0) {
        myTypeSelector.selectType(candidate);
        return;
      }
    }
  }

  public boolean isSuggestedType(final String fqName) {
    for(PsiType type: myTypesForAll) {
      if (type.getCanonicalText().equals(fqName)) {
        return true;
      }
    }

    for(PsiType type: myTypesForMain) {
      if (type.getCanonicalText().equals(fqName)) {
        return true;
      }
    }

    return false;
  }

  public void typeSelected(@NotNull PsiType type) {
    typeSelected(type, getDefaultType());
  }

  public static void typeSelected(final PsiType type, final PsiType defaultType) {
    StatisticsManager.getInstance().incUseCount(new StatisticsInfo(getStatsKey(defaultType), serialize(type)));
  }

  private String getStatsKey() {
    return getStatsKey(getDefaultType());
  }

  private static String getStatsKey(final PsiType defaultType) {
    return "IntroduceVariable##" + serialize(defaultType);
  }

  private static String serialize(PsiType type) {
    if (PsiUtil.resolveClassInType(type) instanceof PsiTypeParameter) return type.getCanonicalText();
    return TypeConversionUtil.erasure(type).getCanonicalText();
  }

  public TypeSelector getTypeSelector() {
    return myTypeSelector;
  }

}
