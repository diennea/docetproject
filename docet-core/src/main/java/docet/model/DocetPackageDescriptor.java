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

import java.util.HashMap;
import java.util.Map;

public class DocetPackageDescriptor {

    private final Map<String, String> labelForLang;
    private final Map<String, String> abstractForLang;

    public DocetPackageDescriptor() {
        this.labelForLang = new HashMap<>();
        this.abstractForLang = new HashMap<>();
    }

    public String getLabelForLang(final String lang) {
        return this.labelForLang.get(lang);
    }

    public String getAbstractForLang(final String lang) {
        return this.abstractForLang.get(lang);
    }

    public void addLabelForLang(final String lang, final String label) {
        this.labelForLang.put(lang, label);
    }

    public void addAbstractForLang(final String lang, final String description) {
        this.abstractForLang.put(lang, description);
    }
}
