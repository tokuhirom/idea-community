/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.lang.java.parser;

import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.lang.PsiBuilder;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ILazyParseableElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.lang.PsiBuilderUtil.expect;
import static com.intellij.lang.PsiBuilderUtil.nextTokenType;
import static com.intellij.lang.java.parser.JavaParserUtil.*;


public class DeclarationParser {
  public enum Context {
    FILE, CLASS, CODE_BLOCK, ANNOTATION_INTERFACE
  }

  private static final TokenSet AFTER_END_DECLARATION_SET = TokenSet.create(JavaElementType.FIELD, JavaElementType.METHOD);
  private static final TokenSet BEFORE_LBRACE_ELEMENTS_SET = TokenSet.create(
    JavaTokenType.IDENTIFIER, JavaTokenType.COMMA, JavaTokenType.EXTENDS_KEYWORD, JavaTokenType.IMPLEMENTS_KEYWORD);
  private static final TokenSet APPEND_TO_METHOD_SET = TokenSet.create(
    JavaTokenType.IDENTIFIER, JavaTokenType.COMMA, JavaTokenType.THROWS_KEYWORD);

  private static final String WHITESPACES = "\n\r \t";
  private static final String LINE_ENDS = "\n\r";

  private DeclarationParser() { }

  public static void parseClassBodyWithBraces(final PsiBuilder builder, final boolean isAnnotation, final boolean isEnum) {
    assert builder.getTokenType() == JavaTokenType.LBRACE : builder.getTokenType();
    builder.advanceLexer();

    final PsiBuilder builderWrapper = braceMatchingBuilder(builder);
    if (isEnum) {
      parseEnumConstants(builderWrapper);
    }
    parseClassBodyDeclarations(builderWrapper, isAnnotation);

    expectOrError(builder, JavaTokenType.RBRACE, JavaErrorMessages.message("expected.rbrace"));
  }

  @Nullable
  public static PsiBuilder.Marker parseClassFromKeyword(final PsiBuilder builder, final PsiBuilder.Marker declaration,
                                                        final boolean isAnnotation, final Context context) {
    final IElementType keywordTokenType = builder.getTokenType();
    assert ElementType.CLASS_KEYWORD_BIT_SET.contains(keywordTokenType) : keywordTokenType;
    builder.advanceLexer();
    final boolean isEnum = (keywordTokenType == JavaTokenType.ENUM_KEYWORD);

    if (!expect(builder, JavaTokenType.IDENTIFIER)) {
      error(builder, JavaErrorMessages.message("expected.identifier"));
      declaration.drop();
      return null;
    }

    ReferenceParser.parseTypeParameters(builder);
    ReferenceParser.parseReferenceList(builder, JavaTokenType.EXTENDS_KEYWORD, JavaElementType.EXTENDS_LIST, JavaTokenType.COMMA);
    ReferenceParser.parseReferenceList(builder, JavaTokenType.IMPLEMENTS_KEYWORD, JavaElementType.IMPLEMENTS_LIST, JavaTokenType.COMMA);

    if (builder.getTokenType() != JavaTokenType.LBRACE) {
      final PsiBuilder.Marker error = builder.mark();
      while (BEFORE_LBRACE_ELEMENTS_SET.contains(builder.getTokenType())) {
        builder.advanceLexer();
      }
      error.error(JavaErrorMessages.message("expected.lbrace"));
    }

    if (builder.getTokenType() == JavaTokenType.LBRACE) {
      parseClassBodyWithBraces(builder, isAnnotation, isEnum);
    }

    if (context == Context.FILE) {
      boolean declarationsAfterEnd = false;

      while (builder.getTokenType() != null && builder.getTokenType() != JavaTokenType.RBRACE) {
        final PsiBuilder.Marker position = builder.mark();
        final PsiBuilder.Marker extra = parse(builder, Context.CLASS);
        if (extra != null && AFTER_END_DECLARATION_SET.contains(exprType(extra))) {
          if (!declarationsAfterEnd) {
            error(builder, JavaErrorMessages.message("expected.class.or.interface"), extra);
          }
          declarationsAfterEnd = true;
          position.drop();
        }
        else {
          position.rollbackTo();
          break;
        }
      }

      if (declarationsAfterEnd) {
        expectOrError(builder, JavaTokenType.RBRACE, JavaErrorMessages.message("expected.rbrace"));
      }
    }

    done(declaration, JavaElementType.CLASS);
    return declaration;
  }

  private static void parseEnumConstants(final PsiBuilder builder) {
    while (builder.getTokenType() != null) {
      if (expect(builder, JavaTokenType.SEMICOLON)) {
        return;
      }

      if (builder.getTokenType() == JavaTokenType.PRIVATE_KEYWORD || builder.getTokenType() == JavaTokenType.PROTECTED_KEYWORD) {
        error(builder, JavaErrorMessages.message("expected.semicolon"));
        return;
      }

      final PsiBuilder.Marker enumConstant = parseEnumConstant(builder);
      if (enumConstant == null) {
        error(builder, JavaErrorMessages.message("expected.identifier"));
      }

      if (!expect(builder, JavaTokenType.COMMA)) {
        if (builder.getTokenType() != null && builder.getTokenType() != JavaTokenType.SEMICOLON) {
          error(builder, JavaErrorMessages.message("expected.comma.or.semicolon"));
          return;
        }
      }
    }
  }

  @Nullable
  public static PsiBuilder.Marker parseEnumConstant(final PsiBuilder builder) {
    final PsiBuilder.Marker constant = builder.mark();

    parseModifierList(builder);

    if (expect(builder, JavaTokenType.IDENTIFIER)) {
      if (builder.getTokenType() == JavaTokenType.LPARENTH) {
        ExpressionParser.parseArgumentList(builder);
      }
      else {
        emptyElement(builder, JavaElementType.EXPRESSION_LIST);
      }

      if (builder.getTokenType() == JavaTokenType.LBRACE) {
        final PsiBuilder.Marker constantInit = builder.mark();
        parseClassBodyWithBraces(builder, false, false);
        done(constantInit, JavaElementType.ENUM_CONSTANT_INITIALIZER);
      }

      done(constant, JavaElementType.ENUM_CONSTANT);
      return constant;
    }
    else {
      constant.rollbackTo();
      return null;
    }
  }

  public static void parseClassBodyDeclarations(final PsiBuilder builder, final boolean isAnnotation) {
    final Context context = isAnnotation ? Context.ANNOTATION_INTERFACE : Context.CLASS;

    PsiBuilder.Marker invalidElements = null;
    while (true) {
      final IElementType tokenType = builder.getTokenType();
      if (tokenType == null || tokenType == JavaTokenType.RBRACE) break;

      if (tokenType == JavaTokenType.SEMICOLON) {
        if (invalidElements != null) {
          invalidElements.error(JavaErrorMessages.message("unexpected.token"));
          invalidElements = null;
        }
        builder.advanceLexer();
        continue;
      }

      final PsiBuilder.Marker declaration = parse(builder, context);
      if (declaration != null) {
        if (invalidElements != null) {
          invalidElements.errorBefore(JavaErrorMessages.message("unexpected.token"), declaration);
          invalidElements = null;
        }
        continue;
      }

      if (invalidElements == null) {
        invalidElements = builder.mark();
      }

      // adding a reference, not simple tokens allows "Browse ..." to work well
      final PsiBuilder.Marker ref = ReferenceParser.parseJavaCodeReference(builder, true, true, false, false, false);
      if (ref == null) {
        builder.advanceLexer();
      }
    }

    if (invalidElements != null) {
      invalidElements.error(JavaErrorMessages.message("unexpected.token"));
    }
  }

  @Nullable
  public static PsiBuilder.Marker parse(final PsiBuilder builder, final Context context) {
    final IElementType tokenType = builder.getTokenType();
    if (tokenType == null) return null;

    if (tokenType == JavaTokenType.LBRACE) {
      if (context == Context.FILE || context == Context.CODE_BLOCK) return null;
    }
    else if (tokenType == JavaTokenType.IDENTIFIER || ElementType.PRIMITIVE_TYPE_BIT_SET.contains(tokenType)) {
      if (context == Context.FILE) return null;
    }
    else if (tokenType instanceof ILazyParseableElementType) {
      builder.advanceLexer();
      return null;
    }
    else if (!ElementType.MODIFIER_BIT_SET.contains(tokenType) &&
             !ElementType.CLASS_KEYWORD_BIT_SET.contains(tokenType) &&
             tokenType != JavaTokenType.AT &&
             (context == Context.CODE_BLOCK || tokenType != JavaTokenType.LT)) {
      return null;
    }

    final PsiBuilder.Marker declaration = builder.mark();
    final int declarationStart = builder.getCurrentOffset();

    final Pair<PsiBuilder.Marker, Boolean> modListInfo = parseModifierList(builder);
    final PsiBuilder.Marker modList = modListInfo.first;

    if (expect(builder, JavaTokenType.AT)) {
      if (builder.getTokenType() == JavaTokenType.INTERFACE_KEYWORD) {
        final PsiBuilder.Marker result = parseClassFromKeyword(builder, declaration, true, context);
        return result != null ? result : modList;
      }
      else {
        declaration.rollbackTo();
        return null;
      }
    }
    else if (ElementType.CLASS_KEYWORD_BIT_SET.contains(builder.getTokenType())) {
      final PsiBuilder.Marker result = parseClassFromKeyword(builder, declaration, false, context);
      return result != null ? result : modList;
    }

    PsiBuilder.Marker typeParams = null;
    if (builder.getTokenType() == JavaTokenType.LT) {
      typeParams = ReferenceParser.parseTypeParameters(builder);
    }

    if (context == Context.FILE) {
      error(builder, JavaErrorMessages.message("expected.class.or.interface"), typeParams);
      declaration.drop();
      return modList;
    }

    PsiBuilder.Marker type;
    if (ElementType.PRIMITIVE_TYPE_BIT_SET.contains(builder.getTokenType())) {
      type = parseTypeNotNull(builder);
    }
    else if (builder.getTokenType() == JavaTokenType.IDENTIFIER) {
      final PsiBuilder.Marker idPos = builder.mark();
      type = parseTypeNotNull(builder);
      if (builder.getTokenType() == JavaTokenType.LPARENTH) {  // constructor
        if (context == Context.CODE_BLOCK) {
          declaration.rollbackTo();
          return null;
        }
        idPos.rollbackTo();
        if (typeParams == null) {
          emptyElement(builder, JavaElementType.TYPE_PARAMETER_LIST);
        }
        builder.advanceLexer();
        if (builder.getTokenType() != JavaTokenType.LPARENTH) {
          declaration.rollbackTo();
          return null;
        }
        return parseMethodFromLeftParenth(builder, declaration, false, true);
      }
      idPos.drop();
    }
    else if (builder.getTokenType() == JavaTokenType.LBRACE) {
      if (context == Context.CODE_BLOCK) {
        error(builder, JavaErrorMessages.message("expected.identifier.or.type"), typeParams);
        declaration.drop();
        return modList;
      }

      final PsiBuilder.Marker codeBlock = StatementParser.parseCodeBlock(builder);
      assert codeBlock != null : builder.getOriginalText();

      if (typeParams != null) {
        final PsiBuilder.Marker error = typeParams.precede();
        error.errorBefore(JavaErrorMessages.message("unexpected.token"), codeBlock);
      }
      done(declaration, JavaElementType.CLASS_INITIALIZER);
      return declaration;
    }
    else {
      final PsiBuilder.Marker error;
      if (typeParams != null) {
        error = typeParams.precede();
      }
      else {
        error = builder.mark();
      }
      error.error(JavaErrorMessages.message("expected.identifier.or.type"));
      declaration.drop();
      return modList;
    }

    if (!expect(builder, JavaTokenType.IDENTIFIER)) {
      if (context == Context.CODE_BLOCK && modListInfo.second) {
        declaration.rollbackTo();
        return null;
      }
      else {
        if (typeParams != null) {
          typeParams.precede().errorBefore(JavaErrorMessages.message("unexpected.token"), type);
        }
        builder.error(JavaErrorMessages.message("expected.identifier"));
        declaration.drop();
        return modList;
      }
    }

    if (builder.getTokenType() == JavaTokenType.LPARENTH) {
      if (context == Context.CLASS || context == Context.ANNOTATION_INTERFACE) {  // method
        if (typeParams == null) {
          emptyElement(type, JavaElementType.TYPE_PARAMETER_LIST);
        }
        return parseMethodFromLeftParenth(builder, declaration, (context == Context.ANNOTATION_INTERFACE), false);
      }
    }

    if (typeParams != null) {
      typeParams.precede().errorBefore(JavaErrorMessages.message("unexpected.token"), type);
    }
    return parseFieldOrLocalVariable(builder, declaration, declarationStart, context);
  }

  @NotNull
  private static PsiBuilder.Marker parseTypeNotNull(final PsiBuilder builder) {
    final PsiBuilder.Marker type = ReferenceParser.parseType(builder, ReferenceParser.EAT_LAST_DOT | ReferenceParser.WILDCARD);
    assert type != null : builder.getOriginalText();
    return type;
  }

  @NotNull
  public static Pair<PsiBuilder.Marker, Boolean> parseModifierList(final PsiBuilder builder) {
    return parseModifierList(builder, ElementType.MODIFIER_BIT_SET);
  }

  @NotNull
  public static Pair<PsiBuilder.Marker, Boolean> parseModifierList(final PsiBuilder builder, final TokenSet modifiers) {
    final PsiBuilder.Marker modList = builder.mark();
    boolean isEmpty = true;

    while (true) {
      final IElementType tokenType = builder.getTokenType();
      if (tokenType == null) break;
      if (modifiers.contains(tokenType)) {
        builder.advanceLexer();
        isEmpty = false;
      }
      else if (tokenType == JavaTokenType.AT) {
        if (ElementType.KEYWORD_BIT_SET.contains(nextTokenType(builder))) {
          break;
        }
        parseAnnotation(builder);
        isEmpty = false;
      }
      else {
        break;
      }
    }

    done(modList, JavaElementType.MODIFIER_LIST);
    return Pair.create(modList, isEmpty);
  }

  private static PsiBuilder.Marker parseMethodFromLeftParenth(final PsiBuilder builder, final PsiBuilder.Marker declaration,
                                                              final boolean anno, final boolean constructor) {
    parseParameterList(builder);

    eatBrackets(builder, constructor, JavaErrorMessages.message("expected.semicolon"));

    if (areTypeAnnotationsSupported(builder)) {
      final PsiBuilder.Marker receiver = builder.mark();
      final PsiBuilder.Marker annotations = parseAnnotations(builder);
      if (annotations != null) {
        done(receiver, JavaElementType.METHOD_RECEIVER);
      }
      else {
        receiver.drop();
      }
    }

    ReferenceParser.parseReferenceList(builder, JavaTokenType.THROWS_KEYWORD, JavaElementType.THROWS_LIST, JavaTokenType.COMMA);

    if (anno && expect(builder, JavaTokenType.DEFAULT_KEYWORD)) {
      parseAnnotationValue(builder);
    }

    final IElementType tokenType = builder.getTokenType();
    if (tokenType != JavaTokenType.SEMICOLON && tokenType != JavaTokenType.LBRACE) {
      final PsiBuilder.Marker error = builder.mark();
      // heuristic: going to next line obviously means method signature is over, starting new method (actually, another one completion hack)
      final CharSequence text = builder.getOriginalText();
      Loop:
      while (true) {
        for (int i = builder.getCurrentOffset() - 1; i >= 0; i--) {
          final char ch = text.charAt(i);
          if (ch == '\n') break Loop;
          else if (ch != ' ' && ch != '\t') break;
        }
        if (!expect(builder, APPEND_TO_METHOD_SET)) break;
      }
      error.error(JavaErrorMessages.message("expected.lbrace.or.semicolon"));
    }

    if (!expect(builder, JavaTokenType.SEMICOLON)) {
      if (builder.getTokenType() == JavaTokenType.LBRACE) {
        StatementParser.parseCodeBlock(builder);
      }
    }

    done(declaration, anno ? JavaElementType.ANNOTATION_METHOD : JavaElementType.METHOD);
    return declaration;
  }

  @NotNull
  public static PsiBuilder.Marker parseParameterList(final PsiBuilder builder) {
    assert builder.getTokenType() == JavaTokenType.LPARENTH : builder.getTokenType();
    final PsiBuilder.Marker paramList = builder.mark();
    builder.advanceLexer();

    PsiBuilder.Marker invalidElements = null;
    String errorMessage = null;
    boolean commaExpected = false;
    int paramCount = 0;
    while (true) {
      final IElementType tokenType = builder.getTokenType();
      if (tokenType == null || tokenType == JavaTokenType.RPARENTH) {
        boolean noLastParam = !commaExpected && paramCount > 0;
        if (noLastParam) {
          error(builder, JavaErrorMessages.message("expected.identifier.or.type"));
        }
        if (tokenType == JavaTokenType.RPARENTH) {
          if (invalidElements != null) {
            invalidElements.error(errorMessage);
          }
          invalidElements = null;
          builder.advanceLexer();
        }
        else {
          if (!noLastParam) {
            if (invalidElements != null) {
              invalidElements.error(errorMessage);
            }
            invalidElements = null;
            error(builder, JavaErrorMessages.message("expected.rparen"));
          }
        }
        break;
      }

      if (commaExpected) {
        if (builder.getTokenType() == JavaTokenType.COMMA) {
          commaExpected = false;
          if (invalidElements != null) {
            invalidElements.error(errorMessage);
            invalidElements = null;
          }
          builder.advanceLexer();
          continue;
        }
      }
      else {
        final PsiBuilder.Marker param = parseParameter(builder, true);
        if (param != null) {
          commaExpected = true;
          if (invalidElements != null) {
            invalidElements.errorBefore(errorMessage, param);
            invalidElements = null;
          }
          paramCount++;
          continue;
        }
      }

      if (invalidElements == null) {
        if (builder.getTokenType() == JavaTokenType.COMMA) {
          error(builder, JavaErrorMessages.message("expected.parameter"));
          builder.advanceLexer();
          continue;
        }
        else {
          invalidElements = builder.mark();
          errorMessage = commaExpected ? JavaErrorMessages.message("expected.comma") : JavaErrorMessages.message("expected.parameter");
        }
      }

      // adding a reference, not simple tokens allows "Browse .." to work well
      final PsiBuilder.Marker ref = ReferenceParser.parseJavaCodeReference(builder, true, true, false, false, false);
      if (ref == null && builder.getTokenType() != null) {
        builder.advanceLexer();
      }
    }

    if (invalidElements != null) {
      invalidElements.error(errorMessage);
    }

    done(paramList, JavaElementType.PARAMETER_LIST);
    return paramList;
  }

  @Nullable
  public static PsiBuilder.Marker parseParameter(final PsiBuilder builder, final boolean ellipsis) {
    final PsiBuilder.Marker param = builder.mark();

    final Pair<PsiBuilder.Marker, Boolean> modListInfo = parseModifierList(builder);

    int flags = ReferenceParser.EAT_LAST_DOT | ReferenceParser.WILDCARD;
    if (ellipsis) flags |= ReferenceParser.ELLIPSIS;
    final ReferenceParser.TypeInfo typeInfo = ReferenceParser.parseTypeInfo(builder, flags);

    if (typeInfo == null && modListInfo.second) {
      param.rollbackTo();
      return null;
    }

    if (typeInfo == null) {
      error(builder, JavaErrorMessages.message("expected.type"));
      emptyElement(builder, JavaElementType.TYPE);
    }

    if (expect(builder, JavaTokenType.IDENTIFIER)) {
      eatBrackets(builder, typeInfo != null && typeInfo.isVarArg, JavaErrorMessages.message("expected.rparen"));
      done(param, JavaElementType.PARAMETER);
      return param;
    }
    else {
      error(builder, JavaErrorMessages.message("expected.identifier"));
      param.drop();
      return modListInfo.first;
    }
  }

  @Nullable
  private static PsiBuilder.Marker parseFieldOrLocalVariable(final PsiBuilder builder, final PsiBuilder.Marker declaration,
                                                             final int declarationStart, final Context context) {
    final IElementType varType;
    if (context == Context.CLASS || context == Context.ANNOTATION_INTERFACE) {
      varType = JavaElementType.FIELD;
    }
    else if (context == Context.CODE_BLOCK) {
      varType = JavaElementType.LOCAL_VARIABLE;
    }
    else {
      declaration.drop();
      assert false : "Unexpected context: " + context;
      return null;
    }

    PsiBuilder.Marker variable = declaration;
    boolean unclosed = false;
    boolean eatSemicolon = true;
    boolean shouldRollback;
    boolean openMarker = true;
    while (true) {
      shouldRollback = true;

      if (!eatBrackets(builder, false, null)) {
        unclosed = true;
      }

      if (expect(builder, JavaTokenType.EQ)) {
        final PsiBuilder.Marker expr = ExpressionParser.parse(builder);
        if (expr != null) {
          shouldRollback = false;
        }
        else {
          error(builder, JavaErrorMessages.message("expected.expression"));
          unclosed = true;
          break;
        }
      }

      if (builder.getTokenType() != JavaTokenType.COMMA) break;
      done(variable, varType);
      builder.advanceLexer();

      if (builder.getTokenType() != JavaTokenType.IDENTIFIER) {
        error(builder, JavaErrorMessages.message("expected.identifier"));
        unclosed = true;
        eatSemicolon = false;
        openMarker = false;
        break;
      }

      variable = builder.mark();
      builder.advanceLexer();
    }

    if (builder.getTokenType() == JavaTokenType.SEMICOLON && eatSemicolon) {
      builder.advanceLexer();
    }
    else {
      // special treatment (see DeclarationParserTest.testMultiLineUnclosed())
      if (!builder.eof() && shouldRollback) {
        final CharSequence text = builder.getOriginalText();
        final int spaceEnd = builder.getCurrentOffset();
        final int spaceStart = CharArrayUtil.shiftBackward(text, spaceEnd-1, WHITESPACES);
        final int lineStart = CharArrayUtil.shiftBackwardUntil(text, spaceEnd, LINE_ENDS);

        if (declarationStart < lineStart && lineStart < spaceStart) {
          final int newBufferEnd = CharArrayUtil.shiftForward(text, lineStart, WHITESPACES);
          declaration.rollbackTo();
          return parse(stoppingBuilder(builder, newBufferEnd), context);
        }
      }

      if (!unclosed) {
        error(builder, JavaErrorMessages.message("expected.semicolon"));
      }
    }

    if (openMarker) {
      done(variable, varType);
    }

    return declaration;
  }

  private static boolean eatBrackets(final PsiBuilder builder, final boolean isError, @Nullable final String error) {
    if (builder.getTokenType() != JavaTokenType.LBRACKET) return true;

    final PsiBuilder.Marker marker = isError ? builder.mark() : null;

    boolean result = true;
    while (expect(builder, JavaTokenType.LBRACKET)) {
      if (!expect(builder, JavaTokenType.RBRACKET)) {
        if (!isError) error(builder, JavaErrorMessages.message("expected.rbracket"));
        result = false;
        break;
      }
    }

    if (marker != null) {
      marker.error(error);
    }

    return result;
  }

  @Nullable
  public static PsiBuilder.Marker parseAnnotations(final PsiBuilder builder) {
    PsiBuilder.Marker firstAnno = null;

    while (builder.getTokenType() == JavaTokenType.AT) {
      final PsiBuilder.Marker anno = parseAnnotation(builder);
      if (firstAnno == null) firstAnno = anno;
    }

    return firstAnno;
  }

  @NotNull
  public static PsiBuilder.Marker parseAnnotation(final PsiBuilder builder) {
    assert builder.getTokenType() == JavaTokenType.AT : builder.getTokenType();
    final PsiBuilder.Marker anno = builder.mark();
    builder.advanceLexer();

    final PsiBuilder.Marker classRef = ReferenceParser.parseJavaCodeReference(builder, true, false, false, false, false);
    if (classRef == null) {
      error(builder, JavaErrorMessages.message("expected.class.reference"));
    }

    parseAnnotationParameterList(builder);

    done(anno, JavaElementType.ANNOTATION);
    return anno;
  }

  @NotNull
  private static PsiBuilder.Marker parseAnnotationParameterList(final PsiBuilder builder) {
    PsiBuilder.Marker list = builder.mark();

    if (!expect(builder, JavaTokenType.LPARENTH)) {
      done(list, JavaElementType.ANNOTATION_PARAMETER_LIST);
      return list;
    }

    if (expect(builder, JavaTokenType.RPARENTH)) {
      done(list, JavaElementType.ANNOTATION_PARAMETER_LIST);
      return list;
    }

    final boolean isFirstParamNamed = parseAnnotationParameter(builder, true);
    boolean isFirstParamWarned = false;

    boolean afterBad = false;
    while (true) {
      final IElementType tokenType = builder.getTokenType();
      if (tokenType == null) {
        error(builder, JavaErrorMessages.message("expected.parameter"));
        break;
      }
      else if (expect(builder, JavaTokenType.RPARENTH)) {
        break;
      }
      else if (tokenType == JavaTokenType.COMMA) {
        final PsiBuilder.Marker errorStart = builder.mark();
        final PsiBuilder.Marker errorEnd = builder.mark();
        builder.advanceLexer();
        final boolean hasParamName = parseAnnotationParameter(builder, false);
        if (!isFirstParamNamed && hasParamName && !isFirstParamWarned) {
          errorStart.errorBefore(JavaErrorMessages.message("annotation.name.is.missing"), errorEnd);
          isFirstParamWarned = true;
        }
        else {
          errorStart.drop();
        }
        errorEnd.drop();
      }
      else if (!afterBad) {
        error(builder, JavaErrorMessages.message("expected.comma.or.rparen"));
        builder.advanceLexer();
        afterBad = true;
      }
      else {
        afterBad = false;
        parseAnnotationParameter(builder, false);
      }
    }

    done(list, JavaElementType.ANNOTATION_PARAMETER_LIST);
    return list;
  }

  private static boolean parseAnnotationParameter(final PsiBuilder builder, final boolean mayBeSimple) {
    PsiBuilder.Marker pair = builder.mark();

    if (mayBeSimple) {
      parseAnnotationValue(builder);
      if (builder.getTokenType() != JavaTokenType.EQ) {
        done(pair, JavaElementType.NAME_VALUE_PAIR);
        return false;
      }

      pair.rollbackTo();
      pair = builder.mark();
    }

    final boolean hasName = expectOrError(builder, JavaTokenType.IDENTIFIER, JavaErrorMessages.message("expected.identifier"));

    expectOrError(builder, JavaTokenType.EQ, JavaErrorMessages.message("expected.eq"));

    parseAnnotationValue(builder);

    done(pair, JavaElementType.NAME_VALUE_PAIR);

    return hasName;
  }

  @NotNull
  public static PsiBuilder.Marker parseAnnotationValue(final PsiBuilder builder) {
    PsiBuilder.Marker result;

    final IElementType tokenType = builder.getTokenType();
    if (tokenType == JavaTokenType.AT) {
      result = parseAnnotation(builder);
    }
    else if (tokenType == JavaTokenType.LBRACE) {
      result = parseAnnotationArrayInitializer(builder);
    }
    else {
      result = ExpressionParser.parseConditional(builder);
    }

    if (result == null) {
      result = builder.mark();
      result.error(JavaErrorMessages.message("expected.value"));
    }

    return result;
  }

  @NotNull
  private static PsiBuilder.Marker parseAnnotationArrayInitializer(final PsiBuilder builder) {
    assert builder.getTokenType() == JavaTokenType.LBRACE : builder.getTokenType();
    final PsiBuilder.Marker annoArray = builder.mark();
    builder.advanceLexer();

    if (expect(builder, JavaTokenType.RBRACE)) {
      done(annoArray, JavaElementType.ANNOTATION_ARRAY_INITIALIZER);
      return annoArray;
    }

    parseAnnotationValue(builder);

    boolean unclosed = false;
    while (true) {
      if (expect(builder, JavaTokenType.RBRACE)) {
        break;
      }
      else if (expect(builder, JavaTokenType.COMMA)) {
        if (builder.getTokenType() != JavaTokenType.RBRACE) {
          parseAnnotationValue(builder);
        }
      }
      else {
        error(builder, JavaErrorMessages.message("expected.rbrace"));
        unclosed = true;
        break;
      }
    }

    done(annoArray, JavaElementType.ANNOTATION_ARRAY_INITIALIZER);
    if (unclosed) {
      annoArray.setCustomEdgeTokenBinders(null, GREEDY_RIGHT_EDGE_PROCESSOR);
    }
    return annoArray;
  }
}
