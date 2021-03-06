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
package com.intellij.ide.util;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ResourceUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.text.Document;
import javax.swing.text.html.HTMLDocument;
import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.text.MessageFormat;

/**
 * @author dsl
 */
public class TipUIUtil {
  @NonNls private static final String SHORTCUT_ENTITY = "&shortcut:";

  private TipUIUtil() {
  }

  public static void openTipInBrowser(String tipPath, JEditorPane browser, Class providerClass) {
    /* TODO: detect that file is not present
    if (!file.exists()) {
      browser.read(new StringReader("Tips for '" + feature.getDisplayName() + "' not found.  Make sure you installed IntelliJ IDEA correctly."), null);
      return;
    }
    */
    try {
      if (tipPath == null) return;
      if (providerClass == null) providerClass = TipUIUtil.class;
      URL url = ResourceUtil.getResource(providerClass, "/tips/", tipPath);

      if (url == null) {
        setCantReadText(browser);
        return;
      }

      StringBuffer text = new StringBuffer(ResourceUtil.loadText(url));
      updateShortcuts(text);
      String replaced = text.toString().replace("&productName;", ApplicationNamesInfo.getInstance().getFullProductName());
      replaced = replaced.replace("&majorVersion;", ApplicationInfo.getInstance().getMajorVersion());
      replaced = replaced.replace("&minorVersion;", ApplicationInfo.getInstance().getMinorVersion());
      browser.read(new StringReader(replaced), null);
      final Document document = browser.getDocument();
      if (document instanceof HTMLDocument) {
        ((HTMLDocument)document).setBase(url);
      }
    }
    catch (IOException e) {
      setCantReadText(browser);
    }
  }

  private static void setCantReadText(JEditorPane browser) {
    try {
      browser.read(new StringReader(
        IdeBundle.message("error.unable.to.read.tip.of.the.day", ApplicationNamesInfo.getInstance().getFullProductName())), null);
    }
    catch (IOException e1) {
      // Can't be
    }
  }

  @NonNls public static final String FONT = SystemInfo.isMac ? "Courier" : "Verdana";
  public static final String COLOR = "#993300";
  public static final String SIZE = SystemInfo.isMac ? "4" : "3";
  @NonNls public static final String SHORTCUT_HTML_TEMPLATE = "<font  style=\"font-family: " + FONT +
                                                              "; font-weight:bold;\" size=\"" + SIZE +
                                                              "\"  color=\"" + COLOR + "\">{0}</font>";

  private static void updateShortcuts(StringBuffer text) {
    int lastIndex = 0;
    while(true) {
      lastIndex = text.indexOf(SHORTCUT_ENTITY, lastIndex);
      if (lastIndex < 0) return;
      final int actionIdStart = lastIndex + SHORTCUT_ENTITY.length();
      int actionIdEnd = text.indexOf(";", actionIdStart);
      if (actionIdEnd < 0) {
        return;
      }
      final String actionId = text.substring(actionIdStart, actionIdEnd);
      final Shortcut[] shortcuts = KeymapManager.getInstance().getActiveKeymap().getShortcuts(actionId);
      String shortcutText = "";
      for (final Shortcut shortcut : shortcuts) {
        if (shortcut instanceof KeyboardShortcut) {
          shortcutText = KeymapUtil.getShortcutText(shortcut);
          break;
        }
      }
      final String replacement = MessageFormat.format(SHORTCUT_HTML_TEMPLATE, shortcutText);
      text.replace(lastIndex, actionIdEnd + 1, replacement);
      lastIndex += replacement.length();
    }
  }
}
