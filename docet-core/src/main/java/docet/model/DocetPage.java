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

import org.apache.lucene.document.Document;

/**
 * Represents a generic document intended as a page (can be a documentation page or a faq page).
 *
 * @author matteo.casadei
 *
 */
public class DocetPage {

    public static final int DOCTYPE_FAQ = 2;
    public static final int DOCTYPE_PAGE = 1;

    private final String id;
    private final String title;
    private final String language;
    private final String summary;
    private final int type;
    private final int relevance;
    private final String matchExplanation;

    private DocetPage(final String id, final String language, final String title, final String summary,
                          final int type, final String matchExplanation, final int relevance) {
        this.id = id;
        this.language = language;
        this.title = title;
        this.summary = summary;
        this.type = type;
        this.matchExplanation = matchExplanation;
        this.relevance = relevance;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public static DocetPage toDocetDocument(final Document doc, final String docExplanation, final int relevance) {
        return new DocetPage(doc.getField("id").stringValue(), 
                doc.getField("language").stringValue(),
                doc.getField("title").stringValue(),
                doc.getField("abstract").stringValue(),
                doc.getField("doctype").numericValue().intValue(), docExplanation, relevance);
    }

    public int getType() {
        return type;
    }

    public String getLanguage() {
        return language;
    }

    public String getMatchExplanation() {
        return matchExplanation;
    }

    public int getRelevance() {
        return relevance;
    }
}
