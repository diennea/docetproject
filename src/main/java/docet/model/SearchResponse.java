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
public class SearchResponse {

    public static final int STATUS_CODE_OK = 0;
    public static final int STATUS_CODE_FAILURE = 1;

    private final List<SearchResult> items;
    private final int status;
    private final String errorMessage;

    public SearchResponse() {
        this(STATUS_CODE_OK, "");
    }

    public SearchResponse(final int status, final String errorMessage) {
        this.status = status;
        this.errorMessage = errorMessage;
        this.items = new ArrayList<>();
    }

    public void addItems(final List<SearchResult> items) {
        this.items.addAll(items);
    }

    public List<SearchResult> getItems() {
        return items;
    }

    public int getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public int getTotalCount() {
        return this.items.size();
    }
}
