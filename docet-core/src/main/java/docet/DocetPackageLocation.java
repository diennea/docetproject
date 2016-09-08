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
package docet;

import java.nio.file.Path;
import java.util.Objects;

public class DocetPackageLocation {

    private final Path packagePath;
    private final Path searchIndexBasePath;
    private final Path docsBasePath;
    private final String packageId;

    public DocetPackageLocation(final String packageId, final Path packagePath, final Path docsBasePaths,
        final Path searchIndexBasePath) {
        this.packageId = packageId;
        this.packagePath = packagePath;
        this.searchIndexBasePath = searchIndexBasePath;
        this.docsBasePath = docsBasePaths;
    }

    public Path getPackagePath() {
        return packagePath;
    }

    public String getPackageId() {
        return packageId;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 67 * hash + Objects.hashCode(this.packagePath);
        hash = 67 * hash + Objects.hashCode(this.packageId);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof DocetPackageLocation)) {
            return false;
        }
        final DocetPackageLocation other = (DocetPackageLocation) obj;
        if (!Objects.equals(this.packageId, other.packageId)) {
            return false;
        }
        if (!Objects.equals(this.packagePath, other.packagePath)) {
            return false;
        }
        return true;
    }

    /**
     * Returns the path to the search index folder for this package.
     *
     * @return the path to the directory containing the search index.
     */
    public Path getSearchIndexFolderPath() {
        return packagePath.resolve(this.searchIndexBasePath);
    }

    /**
     * Retrieves the path to the folder containing the actual package content.
     *
     * @return the path to the directory containing the actual package's docs.
     */
    public Path getDocsFolderPath() {
        return packagePath.resolve(this.docsBasePath);
    }
}
