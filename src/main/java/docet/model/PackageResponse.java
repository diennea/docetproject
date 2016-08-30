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
 * This is a data type class representing package description results returned from
 * docet.
 *
 * @author matteo.casadei
 *
 */
public class PackageResponse extends DocetResponse {

    private final List<PackageDescriptionResult> items;

    public PackageResponse() {
        this(STATUS_CODE_OK, "");
    }

    public PackageResponse(final int status, final String errorMessage) {
        super(status, errorMessage);
        this.items = new ArrayList<>();
    }

    public void addItems(final List<PackageDescriptionResult> items) {
        this.items.addAll(items);
    }

    public List<PackageDescriptionResult> getItems() {
        return items;
    }

    public int getTotalCount() {
        return this.items.size();
    }
}
