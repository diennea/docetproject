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
package docet.engine.model;

import java.util.ArrayList;
import java.util.List;

/**
 * This is a model class representing a TOC.
 *
 * @author matteo.casadei
 *
 */
public class TOC {

    private final List<TOCItem> items;

    public TOC() {
        this.items = new ArrayList<>();
    }

    public TOC(final List<TOCItem> items) {
        this.items = new ArrayList<>();
        this.items.addAll(items);
    }

    public void addItem(final TOCItem item) {
        this.items.add(item);
    }

    public List<TOCItem> getItems() {
        return this.items;
    }

    /**
     * Represents an item of a TOC.
     *
     * @author matteo.casadei
     *
     */
    public static class TOCItem {

        private final int level;
        private final String pagePath;
        private final List<TOCItem> subItems;

        public TOCItem(final String pagePath) {
            this(pagePath, 0);
        }

        public TOCItem(final String pagePath, final int level) {
            this.level = level;
            this.pagePath = pagePath;
            this.subItems = new ArrayList<>();
        }

        public int getLevel() {
            return level;
        }

        public String getPagePath() {
            return pagePath;
        }

        public void addSubItem(final TOCItem subItem) {
            this.subItems.add(subItem);
        }

        public List<TOCItem> getSubItems() {
            return subItems;
        }
    }
}
