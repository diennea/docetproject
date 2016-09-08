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

/**
 * Represents a list of search results for a given package.
 *
 * @author matteo.casadei
 *
 */
public class PackageSearchResult {

    private final List<SearchResult> items;
    private final String packageid;
    private final String packagename;
    private final String packagelink;
    private final boolean ok;
    private final String errorMessage;

    private PackageSearchResult(final String packageid, final String packagename, final String packagelink) {
        this(packageid, packagename, packagelink, new ArrayList<>(), null);
    }

    private PackageSearchResult(final String packageid, final String packagename,
        final String packagelink, final List<SearchResult> items, final String errorMsg) {
        this.packageid = packageid;
        this.packagename = packagename;
        this.packagelink = packagelink;
        this.items = new ArrayList<>();
        if (items != null) {
            this.items.addAll(items);
        }
        if (errorMsg != null) {
            this.errorMessage = errorMsg;
            this.ok = false;
        } else {
            this.errorMessage = null;
            this.ok = true;
        }
    }

    public static PackageSearchResult toPackageSearchResult(final String packageid, final String packagename,
        final String packagelink, final List<SearchResult> items, final String errorMsg) {
        return new PackageSearchResult(packageid, packagename, packagelink, items, errorMsg);
    }

    public void addItems(final List<SearchResult> items) {
        this.items.addAll(items);
    }

    public List<SearchResult> getItems() {
        return items;
    }

    public int getTotalCount() {
        return this.items.size();
    }

    public String getPackageid() {
        return packageid;
    }

    public String getPackagename() {
        return packagename;
    }

    public String getPackagelink() {
        return packagelink;
    }

    public boolean isOk() {
        return ok;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
