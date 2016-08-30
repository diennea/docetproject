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

import java.util.HashMap;
import java.util.Map;

import docet.engine.DocetConfiguration;
import docet.model.DocetDocument;
import docet.model.DocetPackageInfo;
import docet.model.DocetPackageNotFoundException;

public class SimplePackageLocator implements DocetPackageLocator {

    private final Map<String, DocetPackageInfo> installedPackages;
    private final DocetConfiguration docetConf;

    public SimplePackageLocator(final DocetConfiguration docetConf) {
        this.docetConf = docetConf;
        this.installedPackages = new HashMap<>();
    }

    @Override
    public DocetPackageLocation findPackageLocationById(String packageId) throws DocetPackageNotFoundException {
        return null;
    }
    
}
