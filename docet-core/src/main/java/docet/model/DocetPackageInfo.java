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

import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

import docet.DocetPackageLocation;
import docet.engine.DocetDocumentSearcher;
import docet.engine.SimpleDocetDocSearcher;

public class DocetPackageInfo {

    private final long startupTS;
    private final String packageId;
    private final File packageDocsDir;
    private final File packageSearchIndexDir;
    private AtomicLong lastSearchTS;
    private long lastPageLoadedTS;
    private final DocetPackageDescriptor descriptor;
    private final DocetDocumentSearcher searchIndex;
    private final DocetPackageLocation packageLocation;

    public DocetPackageInfo(final String packageId, final DocetPackageLocation packageLocation,
        final DocetPackageDescriptor descriptor, final String docsRelativeFolder, final String searchIndexRelativeFolder) {
        this.packageLocation = packageLocation;
        this.startupTS = System.currentTimeMillis();
        this.packageId = packageId;
        this.lastPageLoadedTS = System.currentTimeMillis();
        this.lastSearchTS = new AtomicLong(System.currentTimeMillis());
        this.packageDocsDir = packageLocation.getPackagePath().resolve(docsRelativeFolder).toFile();
        this.packageSearchIndexDir = packageLocation.getPackagePath().resolve(searchIndexRelativeFolder).toFile();
        this.searchIndex = new SimpleDocetDocSearcher(packageSearchIndexDir.getAbsolutePath());
        this.descriptor = descriptor;
    }

    public DocetPackageDescriptor getDescriptor() {
        return this.descriptor;
    }

    public long getLastSearchTS() {
        return lastSearchTS.get();
    }

    public void setLastSearchTS(long lastSearchTS) {
        this.lastSearchTS.set(lastSearchTS);
    }

    public long getLastPageLoadedTS() {
        return lastPageLoadedTS;
    }

    public void setLastPageLoadedTS(long lastPageLoadedTS) {
        this.lastPageLoadedTS = lastPageLoadedTS;
    }

    public String getPackageId() {
        return packageId;
    }

    public long getStartupTS() {
        return startupTS;
    }

    public File getPackageDocsDir() {
        return packageDocsDir;
    }

    public File getPackageSearchIndexDir() {
        return packageSearchIndexDir;
    }

    public DocetDocumentSearcher getSearchIndex() {
        return searchIndex;
    }

    public DocetPackageLocation getPackageLocation() {
        return packageLocation;
    }
}
