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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import docet.DocetDocumentPlaceholder;
import docet.DocetExecutionContext;
import docet.DocetLanguage;
import docet.DocetPackageLocation;
import docet.DocetPackageLocator;
import docet.DocetUtils;
import docet.error.DocetDocumentSearchException;
import docet.error.DocetPackageException;
import docet.error.DocetPackageNotFoundException;
import docet.model.DocetPackageDescriptor;
import docet.model.DocetPackageInfo;

public class DocetPackageRuntimeManager {

    private static final Logger LOGGER = Logger.getLogger(DocetPackageRuntimeManager.class.getName());
    private static final long OPEN_PACKAGES_REFRESH_TIME_MS = 5l * 60 * 1000; //5 min
    private static final long EXECUTOR_EXEC_INTERVAL = 60l * 1000; //every min
    private final boolean disableExecutor;
    private final PackageRuntimeCheckerExecutor executor;
    private Thread executorThread;
    private final DocetPackageLocator packageLocator;
    private final Map<String, DocetPackageInfo> openPackages;
    private final DocetConfiguration docetConf;
    private final ReadWriteLock lock;

    public DocetPackageRuntimeManager(final DocetPackageLocator packageLocator, final DocetConfiguration docetConf) {
        this.executor = new PackageRuntimeCheckerExecutor();
        this.packageLocator = packageLocator;
        this.openPackages = new HashMap<>();
        this.docetConf = docetConf;
        this.lock = new ReentrantReadWriteLock();
        this.disableExecutor = !docetConf.isEnablePackageLifecycleExecutor();
    }

    public void start() {

        if (!disableExecutor) {
            this.executorThread = new Thread(this.executor, "Docet package lifecycle manager");
            executorThread.setDaemon(true);
            executorThread.start();
        }
    }

    public void stop() throws InterruptedException {
        this.executor.stopExecutor();
        if (this.executorThread != null) {
            this.executorThread.interrupt();
            this.executorThread.join();
        }
    }

    public byte[] getImageForPdfCovers() {
        return this.packageLocator.getImageForPdfCovers();
    }

    public String getPlaceholderValueForDocument(final DocetDocumentPlaceholder code, final DocetLanguage lang) {
        return this.packageLocator.getPlaceholderForPdfDocument(code, lang);
    }

    public DocetPackageDescriptor getDescriptorForPackage(final String packageId, final DocetExecutionContext ctx)
        throws DocetPackageException {
        final DocetPackageInfo packageInfo = this.retrievePackageInfo(packageId, ctx);
        packageInfo.setLastPageLoadedTS(System.currentTimeMillis());
        return packageInfo.getDescriptor();
    }

    public File getDocumentDirectoryForPackage(final String packageName, final DocetExecutionContext ctx)
        throws DocetPackageException {
        final DocetPackageInfo packageInfo = this.retrievePackageInfo(packageName, ctx);
        packageInfo.setLastPageLoadedTS(System.currentTimeMillis());
        return packageInfo.getPackageDocsDir();
    }

    public DocetDocumentSearcher getSearchIndexForPackage(final String packageName, final DocetExecutionContext ctx)
        throws DocetDocumentSearchException {
        try {
            final DocetPackageInfo packageInfo = this.retrievePackageInfo(packageName, ctx);
            final DocetDocumentSearcher searchIndex = packageInfo.getSearchIndex();
            searchIndex.open();
            packageInfo.setLastSearchTS(System.currentTimeMillis());
            return searchIndex;
        } catch (DocetPackageException ex) {
            throw new DocetDocumentSearchException("Impossible to find package " + packageName, ex);
        } catch (IOException ex) {
            throw new DocetDocumentSearchException("Impossible to start search for package " + packageName, ex);
        }
    }

    private DocetPackageInfo retrievePackageInfo(final String packageid, final DocetExecutionContext ctx)
        throws DocetPackageException {
        if (!this.packageLocator.assertPackageAccessPermission(packageid, ctx)) {
            throw DocetPackageException.buildPackageAccessDeniedException();
        }
        this.lock.readLock().lock();
        DocetPackageInfo packageInfo;
        try {
            packageInfo = this.openPackages.get(packageid);
        } finally {
            this.lock.readLock().unlock();
        }
        try {
            DocetPackageLocation retrievedPkgLocation = this.packageLocator.getPackageLocation(packageid);
            this.lock.writeLock().lock();
            try {
                if (packageInfo == null) {
                    packageInfo = this.constructPackageInfo(packageid, retrievedPkgLocation);
                    this.openPackages.put(packageid, packageInfo);
                    LOGGER.log(Level.INFO, "Load Package {0} information", packageid);
                } else {
                    if (!packageInfo.getPackageLocation().equals(retrievedPkgLocation)) {
                        packageInfo.getSearchIndex().close();
                        packageInfo = this.constructPackageInfo(packageid, retrievedPkgLocation);
                        this.openPackages.put(packageid, packageInfo);
                        LOGGER.log(Level.INFO, "Package {0} location has changed, reload configuration", packageid);
                    }
                }
            } finally {
                this.lock.writeLock().unlock();
            }
        } catch (IOException | DocetPackageNotFoundException ex) {
            throw DocetPackageException.buildPackageNotFoundException(ex);
        }
        return packageInfo;
    }

    private DocetPackageInfo constructPackageInfo(final String packageid, final DocetPackageLocation location)
        throws DocetPackageException {
        DocetPackageDescriptor desc;
        try {
            desc = DocetUtils.generatePackageDescriptor(getPathToPackageDoc(location.getPackagePath()));
        } catch (IOException ex) {
            throw DocetPackageException.buildPackageDescriptionException(ex);
        }
        return new DocetPackageInfo(packageid, location, desc,
            this.docetConf.getDocetPackageDocsFolderPath(), this.docetConf.getDocetPackageSearchIndexFolderPath());
    }

    private final class PackageRuntimeCheckerExecutor implements Runnable {

        private volatile boolean stopRequested;

        @Override
        public void run() {
            try {
                while (!this.stopRequested) {
                    final List<DocetPackageInfo> currentAvailablePackages = new ArrayList<>();
                    lock.readLock().lock();
                    try {
                        DocetPackageRuntimeManager
                            .this.openPackages.values().stream().forEach(info -> currentAvailablePackages.add(info));
    
                        currentAvailablePackages.forEach(pck -> {
                            if (pck.getStartupTS() > 0
                                    && OPEN_PACKAGES_REFRESH_TIME_MS <= System.currentTimeMillis() - pck.getLastSearchTS()) {
                                final DocetDocumentSearcher searcher = pck.getSearchIndex();
                                try {
                                    final boolean closed = searcher.close();
                                    if (closed) {
                                        LOGGER.log(Level.INFO, "Closed search index for package {0}, path {1}",
                                            new Object[] {pck.getPackageId(), pck.getPackageSearchIndexDir()});
                                    }
                                } catch (IOException e) {
                                    LOGGER.log(Level.SEVERE, "Error on closing search index for package " + pck, e);
                                }
                            }
                        });
                    } finally {
                        lock.readLock().unlock();
                    }
                    Thread.sleep(EXECUTOR_EXEC_INTERVAL);
                }
            } catch (InterruptedException ex) {
                LOGGER.log(Level.WARNING, "Runtime package controller execution interrupted ", ex);
                Thread.currentThread().interrupt();
            }
            LOGGER.log(Level.INFO, "Runtime package controller execution is terminated");
        }

        public void stopExecutor() {
            this.stopRequested = true;
        }
    }

    private File getPathToPackageDoc(final Path packageBaseDirPath) {
        return packageBaseDirPath.resolve(this.docetConf.getDocetPackageDocsFolderPath()).toFile();
    }
}
