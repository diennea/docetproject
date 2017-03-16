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

public class SearchResult {

    public static final int DOCTYPE_PAGE = 1;
    public static final int DOCTYPE_FAQ = 2;

    private String packageId;
    private String pageId;
    private String pageLink;
    private String language;
    private String title;
    private String pageAbstract;
    private String matchExplanation;
    private String[] breadCrumbs;
    private int relevance;

    private SearchResult(final String packageId, final String pageId, final String language, final String title,
                         final String pageLink, final String pageAbstract, final String matchExplanation,
                         final String[] breadCrumbs, final int relevance) {
        this.packageId = packageId;
        this.pageId = pageId;
        this.language = language;
        this.title = title;
        this.pageLink = pageLink;
        this.pageAbstract = pageAbstract;
        this.matchExplanation = matchExplanation;
        this.relevance = relevance;
        this.breadCrumbs = breadCrumbs;
    }

    public String getPageId() {
        return pageId;
    }

    public void setPageId(String pageId) {
        this.pageId = pageId;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getPageLink() {
        return pageLink;
    }

    public void setPageLink(String pageLink) {
        this.pageLink = pageLink;
    }

    public static SearchResult toSearchResult(final String packageId, final DocetPage doc, final String docId,
            final String pageLink, final String[] breadCrumbs) {
        return new SearchResult(packageId, docId, doc.getLanguage(), doc.getTitle(),
                pageLink, doc.getSummary(), doc.getMatchExplanation(), breadCrumbs, doc.getRelevance());
    }

    public String getPageAbstract() {
        return pageAbstract;
    }

    public void setPageAbstract(String pageAbstract) {
        this.pageAbstract = pageAbstract;
    }

    public String getMatchExplanation() {
        return matchExplanation;
    }

    public int getRelevance() {
        return relevance;
    }

    public String[] getBreadCrumbs() {
        return breadCrumbs;
    }

    public String getPackageId() {
        return packageId;
    }

    public void setPackageId(String packageId) {
        this.packageId = packageId;
    }
}
