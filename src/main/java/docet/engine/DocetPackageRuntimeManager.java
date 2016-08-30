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

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import docet.DocetPackageLocator;
import docet.model.DocetPackageNotFoundException;
import docet.model.DocetPackageInfo;

public class DocetPackageRuntimeManager {

    private static final long MAX_ALLOWED_SEARCH_INDEX_OPEN_TIME_MS = 30 * 60 * 1000; //30 min
    private static final boolean DISABLE_EXECUTOR = true;
    private final Map<String, DocetPackageInfo> installedPackages;
    private final PackageRuntimeCheckerExecutor executor;
    private final DocetConfiguration docetConf;

    public void start() throws Exception {
        //only in case we are in developer mode then
        //load packages at starup
        final Set<String> availablePackages = this.docetConf.getInstalledPackages();
        if (!availablePackages.isEmpty()) {
            for (final String packageId : availablePackages) {
                final String packageBasePath = this.docetConf.getPathToDocPackage(packageId);
                this.installedPackages.put(packageId, new DocetPackageInfo(packageId,
                        this.getPathToPackageDoc(packageBasePath),
                        this.getPathToPackageSearchIndex(packageBasePath)));
            }
        }
        if (!DISABLE_EXECUTOR) {
            final Thread executor = new Thread(this.executor);
            executor.setDaemon(true);
            executor.start();
        }
    }

    private File getPathToPackageDoc(final String packageBaseDirPath) {
        return new File(packageBaseDirPath).toPath().resolve(this.docetConf.getDocetPackageDocsFolderPath()).toFile();
    }

    private File getPathToPackageSearchIndex(final String packageBaseDirPath) {
        return new File(packageBaseDirPath).toPath().resolve(this.docetConf.getDocetPackageSearchIndexFolderPath()).toFile();
    }

    public void stop() {
        this.executor.stop();
    }

    //FIXME
    public DocetPackageRuntimeManager(final DocetPackageLocator packageLocator) {
        this.installedPackages = new HashMap<>();
        this.executor = new PackageRuntimeCheckerExecutor();
        this.docetConf = null;
    }

    public DocetPackageRuntimeManager(final DocetConfiguration docetConf) {
        this.installedPackages = new HashMap<>();
        this.executor = new PackageRuntimeCheckerExecutor();
        this.docetConf = docetConf;
    }

    public void updateDocetPackage(final String packageName, final File packageDocsDir, final File packageSearchIndexDir)
            throws IOException, DocetPackageNotFoundException {
        final DocetPackageInfo foundPackage = this.installedPackages.get(packageName);
        if (foundPackage != null) {
            final DocetDocumentSearcher searchIndex = foundPackage.getSearchIndex();
            if (searchIndex.isOpen()) {
                searchIndex.close();
            }
        }
        this.addDocetPackage(packageName, packageDocsDir, packageSearchIndexDir);
    }

    public void addDocetPackage(final String packageName, final File packageDocsDir, final File packageSearchIndexDir)
            throws DocetPackageNotFoundException, IOException {
        if (this.installedPackages.get(packageName) != null) {
            throw new DocetPackageNotFoundException(DocetPackageNotFoundException.DOC_PACKAGE_ALREADY_PRESENT);
        }
        this.installedPackages.put(packageName, new DocetPackageInfo(packageName, packageDocsDir, packageSearchIndexDir));
    }

    public DocetDocumentSearcher getSearchIndexForPackage(final String packageName) throws DocetPackageNotFoundException, IOException {
        final DocetPackageInfo packageInfo = this.installedPackages.get(packageName);
        if (packageInfo == null) {
            throw new DocetPackageNotFoundException(DocetPackageNotFoundException.DOC_PACKAGE_NOT_FOUND);
        }
        final DocetDocumentSearcher searchIndex = packageInfo.getSearchIndex();
        if (packageInfo.getLastSearchTS() < 0) {
            searchIndex.open();
        }
        packageInfo.setLastSearchTS(System.currentTimeMillis());
        return searchIndex;
    }

    public Set<String> getInstalledPackages() {
        return this.installedPackages.keySet();
    }

    public File getDocumentDirectoryForPackage(final String packageName) throws DocetPackageNotFoundException {
        final DocetPackageInfo packageInfo = this.installedPackages.get(packageName);
        if (packageInfo == null) {
            throw new DocetPackageNotFoundException(DocetPackageNotFoundException.DOC_PACKAGE_NOT_FOUND);
        }
        packageInfo.setLastPageLoadedTS(System.currentTimeMillis());
        return packageInfo.getPackageDocsDir();
    }

    private final class PackageRuntimeCheckerExecutor implements Runnable {

        private volatile boolean stopRequested;

        @Override
        public void run() {
            try {
                while (!this.stopRequested) {
                    DocetPackageRuntimeManager.this.installedPackages.values().forEach(pck -> {
                        final DocetDocumentSearcher searcher = pck.getSearchIndex();
                        if (pck.getLastSearchTS() > 0 &&  MAX_ALLOWED_SEARCH_INDEX_OPEN_TIME_MS <= System.currentTimeMillis() - pck.getLastSearchTS()) {
                            try {
                                searcher.close();
                                System.out.println("CLosed search Index for package: " + pck.getPackageId());
                                pck.setLastSearchTS(-1);
                            } catch (IOException e) {
                                new RuntimeException(e);
                            }
                        }
                    });
                    Thread.sleep(5000);
                }
            } catch (InterruptedException ex) {
                
            }
            System.out.println("Runtime package controller execution is terminated");
        }

        public void stop() {
            this.stopRequested = true;
        }
    }
}
