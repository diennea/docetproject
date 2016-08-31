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
 * This is a data type class representing all the search results returned from
 * doc search.
 *
 * @author matteo.casadei
 *
 */
public class SearchResponse extends DocetResponse {

    public static final String CURRENT_PACKAGE_NONE = "none";
    private final List<PackageSearchResult> results;
    private final String currentpackage;
    private PackageSearchResult currentPackageResults;

    public SearchResponse() {
        this(CURRENT_PACKAGE_NONE, STATUS_CODE_OK, "");
    }

    public SearchResponse(final String currentpackage) {
        this(currentpackage, STATUS_CODE_OK, "");
    }

    public SearchResponse(final String currentpackage, final int status, final String errorMessage) {
        super(status, errorMessage);
        this.results = new ArrayList<>();
        this.currentpackage = currentpackage;
    }

    public void addResults(final List<PackageSearchResult> results) {
        this.results.addAll(results);
    }

    public List<PackageSearchResult> getResults() {
        return results;
    }

    public int getTotalCount() {
        int count = this.results.stream().mapToInt(res -> res.getTotalCount()).sum();
        if (this.currentPackageResults != null) {
            count += this.currentPackageResults.getTotalCount();
        }
        return count;
    }

    public String getCurrentpackage() {
        return currentpackage;
    }

    public PackageSearchResult getCurrentPackageResults() {
        return currentPackageResults;
    }

    public void setCurrentPackageResults(PackageSearchResult currentPackageResults) {
        this.currentPackageResults = currentPackageResults;
    }
}
