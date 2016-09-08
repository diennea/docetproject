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
 * This is a model class representing a TOC.
 *
 * @author matteo.casadei
 *
 */
public class TableOfContents {

    private final List<TOCSection> sections;
    private final TOCSection mainSection;

    public TableOfContents(final String title, final Document page, final String pathToMainPage) {
        this.sections = new ArrayList<>();
        this.mainSection = new TOCSection(title, page, pathToMainPage, 0);
    }

    public void addSection(final TOCSection section) {
        this.sections.add(section);
    }

    public List<TOCSection> getSections() {
        return this.sections;
    }

    public TOCSection getMainSection() {
        return this.mainSection;
    }
}
