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
package com.intellij.spellchecker.inspections;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Verifier;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public class PlainTextSplitter extends BaseSplitter {


  @NonNls
  private static final Pattern MAIL =
    Pattern.compile("([\\p{L}0-9\\.\\-\\_]+@([\\p{L}0-9\\-\\_]+\\.)+(com|net|[a-z]{2}))");

  @NonNls
  private static final Pattern URL =
    Pattern.compile("((ftp|http|file|https)://([^/]+)(/.*)?(/.*))");


  public List<CheckArea> split(@Nullable String text, @NotNull TextRange range) {
    if (text == null || StringUtil.isEmpty(text)) {
      return null;
    }
    if (Verifier.checkCharacterData(range.substring(text)) != null) {
      return null;
    }

    List<TextRange> toCheck;
    if (text.indexOf('@')>0) {
      toCheck = excludeByPattern(text, range, MAIL, 0);
    }
    else
    if (text.indexOf(':')>0) {
      toCheck = excludeByPattern(text, range, URL, 0);
    }
    else
    {
      toCheck = Collections.singletonList(range);
    }

    if (toCheck == null) return null;

    List<CheckArea> results = new ArrayList<CheckArea>();
    final TextSplitter ws = SplitterFactory.getInstance().getTextSplitterNew();
    for (TextRange r : toCheck) {

      checkCancelled();

      final List<CheckArea> res = ws.split(text, r);
      if (res != null) {
        results.addAll(res);
      }

    }

    return (results.size() == 0) ? null : results;
  }


}
