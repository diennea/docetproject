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

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import docet.engine.DocetDocFormat;

/**
 *
 * A generic docet document intended as a well-defined structure of pages organized around a defined summary, featuring
 * a title and a format.
 *
 * @author matteo.casadei
 *
 */
public class DocetDocument {

    private final String title;
    private final String lang;
    private final String packageName;
    private final DocetDocFormat format;
    private final List<SummaryEntry> summary;

    private DocetDocument(final String title, final String packageName, final String lang) {
        this(title, lang, packageName, DocetDocFormat.TYPE_PDF);
    }

    private DocetDocument(final String title, final String lang, final String packageName, final DocetDocFormat format) {
        this.title = title;
        this.format = format;
        this.lang = lang;
        this.packageName = packageName;
        this.summary = new ArrayList<>();
    }

    public String getTitle() {
        return title;
    }

    public String getLang() {
        return lang;
    }

    public DocetDocFormat getFormat() {
        return format;
    }

    public List<SummaryEntry> getSummary() {
        return summary;
    }

    public String getPackageName() {
        return packageName;
    }

    public static DocetDocument parseTocToDocetDocument(final String htmlToc, final String packageName, final String lang) {
        final Document tocDoc = Jsoup.parse(htmlToc, "UTF-8");
        final String title = tocDoc.head().getElementsByTag("title").first().html();
        final DocetDocument res = new DocetDocument(title, packageName, lang);
        final List<SummaryEntry> summary = res.getSummary();
        final Elements entries = tocDoc.body().select("nav > ul > li");
        entries.stream().forEach(li -> {
            summary.add(SummaryEntry.parseEntryFromElement(li, 0, lang));
        });
        return res;
    }

    @Override
    public String toString() {
        return "{title " + this.title + ", lang " + this.lang + ", format " + this.format
            + ", [" + this.summary.stream().map(s -> s.toString()).reduce((a, b) -> a +", " + b).orElse("") + "]}";
    }
}
