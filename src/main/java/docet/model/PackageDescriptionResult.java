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

public class PackageDescriptionResult {

    private final String title;
    private final String packageId;
    private final String packageLink;
    private final String description;
    private final String imageLink;
    private final String lang;

    public PackageDescriptionResult(final String title, final String packageId, final String packageLink,
            final String description, final String imageLink, final String lang) {
        this.title = title;
        this.packageId = packageId;
        this.packageLink = packageLink;
        this.description = description;
        this.imageLink = imageLink;
        this.lang = lang;
    }

    public String getPackageId() {
        return packageId;
    }

    public String getPackageLink() {
        return packageLink;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getImageLink() {
        return imageLink;
    }

    public String getLang() {
        return lang;
    }
}
