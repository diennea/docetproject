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

import org.jsoup.nodes.Document;

/**
 * Models a generic section of a TOC.
 *
 * @author matteo.casadei
 *
 */
public class TOCSection {

    private final List<TOCSection> subSections;
    private final int level;
    private int sequenceNumber;
    private final String title;
    private final Document page;
    private final String pagePath;

    public TOCSection(final String title, final Document page, final String pagePath) {
        this(title, page, pagePath, 1);
    }

    public TOCSection(final String title, final Document page, final String pagePath, final int level) {
        this.subSections = new ArrayList<>();
        this.page = page;
        this.level = level;
        this.title = title;
        this.pagePath = pagePath;
    }

    public void addSubSection(final TOCSection subSection) {
        this.subSections.add(subSection);
    }

    public List<TOCSection> getSubSections() {
        return this.subSections;
    }

    public int getLevel() {
        return level;
    }

    public String getTitle() {
        return title;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public Document getPage() {
        return page;
    }

    public String getPagePath() {
        return pagePath;
    }
}
