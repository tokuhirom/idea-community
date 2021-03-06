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
package com.intellij.psi.impl.source.html;

import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.impl.source.xml.XmlTagImpl;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Maxim.Mossienko
 */
public class HtmlTagImpl extends XmlTagImpl implements HtmlTag {
  public HtmlTagImpl() {
    super(XmlElementType.HTML_TAG);
  }

  @NotNull
  public XmlTag[] findSubTags(String name, String namespace) {
    final XmlTag[] subTags = getSubTags();
    List<XmlTag> result = null;

    for (final XmlTag subTag : subTags) {
      if (namespace == null) {
        String tagName = subTag.getName();
        tagName = tagName.toLowerCase();

        if (name == null || name.equals(tagName)) {
          if (result == null) {
            result = new ArrayList<XmlTag>(3);
          }

          result.add(subTag);
        }
      }
      else if (namespace.equals(subTag.getNamespace()) &&
               (name == null || name.equals(subTag.getLocalName()))
        ) {
        if (result == null) {
          result = new ArrayList<XmlTag>(3);
        }

        result.add(subTag);
      }
    }

    return result == null ? EMPTY : result.toArray(new XmlTag[result.size()]);
  }

  protected boolean isCaseSensitive() {
    return false;
  }

  public String getAttributeValue(String qname) {
    qname = qname.toLowerCase();
    return super.getAttributeValue(qname);
  }

  protected void cacheOneAttributeValue(String name, String value, final Map<String, String> attributesValueMap) {
    name = name.toLowerCase();
    super.cacheOneAttributeValue(name, value, attributesValueMap);
  }

  public String getAttributeValue(String name, String namespace) {
    name = name.toLowerCase();
    return super.getAttributeValue(name, namespace);
  }

  @NotNull
  public String getNamespace() {
    if(getNamespacePrefix().length() == 0)
      return XmlUtil.HTML_URI;
    return super.getNamespace();
  }

  protected String getRealNs(final String value) {
    if (XmlUtil.XHTML_URI.equals(value)) return XmlUtil.HTML_URI;
    return value;
  }

  public String toString() {
    return "HtmlTag:" + getName();
  }

  public String getPrefixByNamespace(String namespace) {
    if (XmlUtil.HTML_URI.equals(namespace)) namespace = XmlUtil.XHTML_URI;
    return super.getPrefixByNamespace(namespace);
  }
}
