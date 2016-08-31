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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import docet.DocetPackageLocation;
import docet.DocetPackageLocator;
import docet.DocetUtils;
import docet.SimplePackageLocator;
import docet.model.DocetPackageDescriptor;
import docet.model.DocetPackageInfo;
import docet.model.DocetPackageNotFoundException;

public class DocetPackageRuntimeManager {

    private static final long OPEN_PACKAGES_REFRESH_TIME_MS = 30 * 60 * 1000; //30 min
    private static final boolean DISABLE_EXECUTOR = true;
    private final PackageRuntimeCheckerExecutor executor;
    private final DocetPackageLocator packageLocator;
    private final Map<String, DocetPackageInfo> openPackages;
    private final DocetConfiguration docetConf;

    public void start() throws Exception {

        if (!DISABLE_EXECUTOR) {
            final Thread executor = new Thread(this.executor);
            executor.setDaemon(true);
            executor.start();
        }
    }

    public void stop() {
        this.executor.stop();
    }

    public DocetPackageRuntimeManager(final DocetPackageLocator packageLocator, final DocetConfiguration docetConf) {
        this.executor = new PackageRuntimeCheckerExecutor();
        this.packageLocator = packageLocator;
        this.openPackages = new ConcurrentHashMap<>();
        this.docetConf = docetConf;
    }

    public DocetPackageDescriptor getDescriptorForPackage(final String packageId)  throws DocetPackageNotFoundException {
        final DocetPackageInfo packageInfo = this.retrievePackageInfo(packageId);
        packageInfo.setLastPageLoadedTS(System.currentTimeMillis());
        return packageInfo.getDescriptor();
    }

    public File getDocumentDirectoryForPackage(final String packageName) throws DocetPackageNotFoundException {
        final DocetPackageInfo packageInfo = this.retrievePackageInfo(packageName);
        packageInfo.setLastPageLoadedTS(System.currentTimeMillis());
        return packageInfo.getPackageDocsDir();
    }

    public DocetDocumentSearcher getSearchIndexForPackage(final String packageName) throws DocetPackageNotFoundException, IOException {
        final DocetPackageInfo packageInfo = this.retrievePackageInfo(packageName);
        final DocetDocumentSearcher searchIndex = packageInfo.getSearchIndex();
        if (packageInfo.getLastSearchTS() < 0) {
            searchIndex.open();
        }
        packageInfo.setLastSearchTS(System.currentTimeMillis());
        return searchIndex;
    }

    /**
     * Used only for test purposed.
     * @return
     */
    public Set<String> getInstalledPackages() {
        final Set<String> res = new HashSet<>();
        if (this.packageLocator instanceof SimplePackageLocator) {
            final SimplePackageLocator simpleLocator = (SimplePackageLocator) this.packageLocator;
            res.addAll(simpleLocator.getInstalledPackages().stream().map(loc -> loc.getPackageId()).collect(Collectors.toList()));
        }
        res.addAll(this.openPackages.values().stream().map(pkg -> pkg.getPackageId()).collect(Collectors.toList()));
        return res;
    }

    private DocetPackageInfo retrievePackageInfo(final String packageid) throws DocetPackageNotFoundException {
        DocetPackageInfo packageInfo = this.openPackages.get(packageid);
        if (packageInfo == null) {
            final DocetPackageLocation packageLocation = this.packageLocator.findPackageLocationById(packageid);
            DocetPackageDescriptor desc;
            try {
                desc = DocetUtils.generatePackageDescriptor(getPathToPackageDoc(packageLocation.getPackagePath()));
            } catch (Exception ex) {
                desc = new DocetPackageDescriptor();
            }
            try {
                packageInfo = new DocetPackageInfo(packageid, getPathToPackageDoc(
                        packageLocation.getPackagePath()),
                        getPathToPackageSearchIndex(packageLocation.getPackagePath()),
                        desc);
                this.openPackages.put(packageid, packageInfo);
            } catch (IOException ex) {
                throw new DocetPackageNotFoundException("Error on accessing folder for package '" + packageid + "'", ex);
            }
        }
        return packageInfo;
    }

    private final class PackageRuntimeCheckerExecutor implements Runnable {

        private volatile boolean stopRequested;

        @Override
        public void run() {
            try {
                while (!this.stopRequested) {
                    final List<DocetPackageInfo> currentAvailablePackages = new ArrayList<>();
                    DocetPackageRuntimeManager.this.openPackages.values().stream().forEach(info -> currentAvailablePackages.add(info));

                    currentAvailablePackages.forEach(pck -> {
                        if (pck.getStartupTS() > 0 &&  OPEN_PACKAGES_REFRESH_TIME_MS <= System.currentTimeMillis() - pck.getStartupTS()) {
                            final DocetDocumentSearcher searcher = pck.getSearchIndex();
                            try {
                                if (searcher.isOpen()) {
                                    searcher.close();
                                }
                            } catch (IOException e) {
                                System.out.println("Error on closing index for open package: " + e.getMessage());
                            }
                            DocetPackageRuntimeManager.this.openPackages.remove(pck.getPackageId());
                            System.out.println("Removed entry for open package: " + pck.getPackageId());
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

    private File getPathToPackageSearchIndex(final Path packageBaseDirPath) {
        return packageBaseDirPath.resolve(this.docetConf.getDocetPackageSearchIndexFolderPath()).toFile();
    }

    private File getPathToPackageDoc(final Path packageBaseDirPath) {
        return packageBaseDirPath.resolve(this.docetConf.getDocetPackageDocsFolderPath()).toFile();
    }
}
