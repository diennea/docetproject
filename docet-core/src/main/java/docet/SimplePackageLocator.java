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

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import docet.engine.DocetConfiguration;
import docet.error.DocetPackageNotFoundException;

public class SimplePackageLocator implements DocetPackageLocator {

    private static final Logger LOGGER = Logger.getLogger(SimplePackageLocator.class.getName());
    private final Map<String, DocetPackageLocation> installedPackages;
    private final DocetConfiguration docetConf;

    public SimplePackageLocator(final DocetConfiguration docetConf) throws IOException {
        this.docetConf = docetConf;
        this.installedPackages = new HashMap<>();
        this.initializeInstalledPackages();
    }

    private void initializeInstalledPackages() throws IOException {
        //only in case we are in developer mode then load packages at startup
        final Set<String> availablePackages = this.docetConf.getInstalledPackages();
        if (!availablePackages.isEmpty()) {
            for (final String packageId : availablePackages) {
                File directory = new File(this.docetConf.getPathToDocPackage(packageId));
                LOGGER.log(Level.SEVERE, "initialize package {0} in {1}", new Object[]{packageId, directory.getAbsolutePath()});
                boolean initializationError = false;
                if (!directory.isDirectory()) {
                    LOGGER.log(Level.SEVERE, "Cannot find package {0} directory {1}",
                        new Object[]{packageId, directory.getAbsolutePath()});
                    initializationError = true;
                }
                final Path docsBasePath = Paths.get(this.docetConf.getDocetPackageDocsFolderPath());
                final File docsDirectory = directory.toPath().resolve(docsBasePath).toFile();
                if (!docsDirectory.isDirectory()) {
                    LOGGER.log(Level.SEVERE, "Cannot find package {0} docs folder {1}",
                        new Object[]{packageId, docsDirectory.getAbsolutePath()});
                    initializationError = true;
                }
                final Path searchBasePath = Paths.get(this.docetConf.getDocetPackageSearchIndexFolderPath());
                final File searchDirectory = directory.toPath().resolve(searchBasePath).toFile();
                if (!searchDirectory.isDirectory()) {
                    LOGGER.log(Level.SEVERE, "Cannot find package {0} search index folder {1}",
                        new Object[]{packageId, searchDirectory.getAbsolutePath()});
                    initializationError = true;
                }
                if (!initializationError) {
                    final DocetPackageLocation packageBasePath
                        = new DocetPackageLocation(packageId, directory.toPath());
                    this.installedPackages.put(packageId, packageBasePath);
                    LOGGER.log(Level.SEVERE, "initialize package {0} in {1} success", new Object[]{packageId, directory.getAbsolutePath()});
                } else {
                    LOGGER.log(Level.SEVERE, "initialize package {0} in {1} failure", new Object[]{packageId, directory.getAbsolutePath()});
                }
            }
        }
    }

    public List<DocetPackageLocation> getInstalledPackages() {
        final List<DocetPackageLocation> res = new ArrayList<>();
        res.addAll(this.installedPackages.values());
        return res;
    }

    @Override
    public DocetPackageLocation getPackageLocation(String packageId) throws DocetPackageNotFoundException {
        final DocetPackageLocation res = this.installedPackages.get(packageId);
        if (res == null) {
            throw new DocetPackageNotFoundException("Package '" + packageId + "' not available");
        }
        return res;
    }

}
