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
package docet.engine;

import java.util.Arrays;

/**
 *
 *
 */
public enum DocetDocFormat {
    TYPE_HTML("html", false),
    TYPE_PDF("pdf", true);

    private String name;
    private boolean includeResources;

    private DocetDocFormat(final String name, final boolean includeResources) {
        this.name = name;
        this.includeResources = includeResources;
    }

    @Override
    public String toString() {
        return this.name;
    }

    public boolean isIncludeResources() {
        return this.includeResources;
    }

    public static DocetDocFormat parseDocetRequestByName(final String name) {
        return Arrays.asList(DocetDocFormat.values())
                .stream()
                .filter(req -> req.toString().equals(name)).findFirst().orElse(null);
    }
}