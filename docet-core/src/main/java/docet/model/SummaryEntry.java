/*
 * Licensed to Diennea S.r.l. under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Diennea S.r.l. licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package docet.model;

import java.util.ArrayList;
import java.util.List;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class SummaryEntry {

    private final String targetPageId;
    private final String name;
    private final String lang;
    private final int level;
    private final List<SummaryEntry> subSummary;

    private SummaryEntry(final String name, final String targetPageId, final String lang, final int level) {
        this.targetPageId = targetPageId;
        this.lang = lang;
        this.name = name;
        this.subSummary = new ArrayList<>();
        this.level = level;
    }

    public String getTargetPageId() {
        return targetPageId;
    }

    public List<SummaryEntry> getSubSummary() {
        return subSummary;
    }

    public String getName() {
        return name;
    }

    public int getLevel() {
        return level;
    }

    public String getLang() {
        return lang;
    }

    public static SummaryEntry parseEntryFromElement(final Element item, final int level, final String defaultLang) {
        final Element anchor = item.select("a").first();
        final String pageId = anchor.attr("href").split(".html")[0];
        final String name = anchor.html();
        final String referenceLang = anchor.attr("reference-language");
        final String parsedlang;
        if (referenceLang.isEmpty()) {
            parsedlang = defaultLang;
        } else {
            parsedlang = referenceLang;
        }
        final SummaryEntry entry = new SummaryEntry(name, pageId, parsedlang, level);
        final Elements subList = item.select(":root > ul");
        if (subList.size() == 1) {
            final Elements subItems = subList.first().select(":root > li");
            subItems.forEach(li -> {
                entry.getSubSummary().add(SummaryEntry.parseEntryFromElement(li, level + 1, defaultLang));
            });
        }
        return entry;
    }

    @Override
    public String toString() {
        return "{name " + this.name + ", target " + this.targetPageId 
            + ", [" + this.subSummary.stream().map(s -> s.toString()).reduce((a, b) -> a +", " + b).orElse("") + "]}";
    }
}
