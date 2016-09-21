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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Docet doc configuration wrapper.
 *
 * @author matteo.casadei
 *
 */
public class DocetConfiguration {

    private final Map<String, String> pathToInstalledDocPackages;
    private final String pathToPages;
    private final String pathToImages;
    private final String pathToFaq;
    private final String tocFilePath;
    private final String linkToPagePattern;
    private final String linkToImagePattern;
    private final String linkToFaqPattern;
    private final String docetStaticResAdditionalParams;
    private final String docetPackageDocsFolderPath;
    private final String docetPackageSearchIndexFolderPath;
    private final String version;
    private final String defaultLanguage;
    private final int maxSearchResultsForPackage;
    private final boolean faqTocAtRuntime;
    private final boolean previewMode;
    private final boolean debugMode;
    private final boolean enablePackageLifecycleExecutor;

    /**
     * Create a new instance of configuration from a {@link Properties} instance.
     *
     * @param conf the properties instance whereby define the new Docet configuration
     */
    public DocetConfiguration(final Properties conf) {
        this.defaultLanguage = conf.getProperty("docet.language.default", "en");
        this.docetStaticResAdditionalParams = conf.getProperty("docet.staticresources.additionalParams", null);
        this.pathToPages = conf.getProperty("docet.pages.path", "{0}/pages");
        this.pathToImages = conf.getProperty("docet.images.path", "{0}/imgs");
        this.pathToFaq = conf.getProperty("docet.faq.path", "/{0}/faq");
        this.tocFilePath = conf.getProperty("docet.toc.path", "/{0}/toc.html");
        this.linkToPagePattern = conf.getProperty("docet.linktopage.pattern", "pages/{0}/{1}_{2}.mndoc");
        this.linkToFaqPattern = conf.getProperty("docet.linktofaq.pattern", "faq/{0}/{1}_{2}.mndoc");
        this.linkToImagePattern = conf.getProperty("docet.linktoimage.pattern", "images/{0}/{1}_{2}");
        this.previewMode = Boolean.parseBoolean(conf.getProperty("docet.previewmode", "false"));
        this.faqTocAtRuntime = Boolean.parseBoolean(conf.getProperty("docet.faq.toc.runtime", "true"));
        this.pathToInstalledDocPackages = new HashMap<>();
        this.debugMode = Boolean.parseBoolean(conf.getProperty("docet.debugmode", "false"));
        this.docetPackageDocsFolderPath = conf.getProperty("docet.package.docs.dirpath", "docs");
        this.docetPackageSearchIndexFolderPath = conf.getProperty("docet.package.searchindex.dirpath", "");
        this.maxSearchResultsForPackage = Integer.parseInt(conf.getProperty("docet.search.resultsforpackage.max", "20"));
        this.version = conf.getProperty("docet.version", "-");
        this.enablePackageLifecycleExecutor = Boolean.parseBoolean(conf.getProperty("docet.package.enable.lifecycle.executor", "true"));
    }

    public String getVersion() {
        return version;
    }

    public String getPathToPages() {
        return pathToPages;
    }

    public String getPathToImages() {
        return pathToImages;
    }

    public String getTocFilePath() {
        return tocFilePath;
    }

    public String getLinkToPagePattern() {
        return linkToPagePattern;
    }

    public String getLinkToImagePattern() {
        return linkToImagePattern;
    }

    public boolean isPreviewMode() {
        return previewMode;
    }

    /**
     * @deprecated @return
     */
    public String getDocetStaticResAdditionalParams() {
        return docetStaticResAdditionalParams;
    }

    public String getDocetPackageDocsFolderPath() {
        return docetPackageDocsFolderPath;
    }

    public String getDocetPackageSearchIndexFolderPath() {
        return docetPackageSearchIndexFolderPath;
    }

    public String getPathToFaq() {
        return pathToFaq;
    }

    public boolean isFaqTocAtRuntime() {
        return faqTocAtRuntime;
    }

    public String getLinkToFaqPattern() {
        return linkToFaqPattern;
    }

    /**
     * only for test purposed.
     *
     * @param packageName
     * @return
     */
    public String getPathToDocPackage(final String packageName) {
        return this.pathToInstalledDocPackages.get(packageName);
    }

    /**
     * only for test purposed.
     *
     * @param packageName
     * @return
     */
    public Set<String> getInstalledPackages() {
        final Set<String> foundPackages = new HashSet<>();
        foundPackages.addAll(this.pathToInstalledDocPackages.keySet());
        return foundPackages;
    }

    /**
     * only for test purposed.
     *
     * @param packageName
     * @return
     */
    public void addPackage(final String packageName, final String path) {
        this.pathToInstalledDocPackages.put(packageName, path);
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public int getMaxSearchResultsForPackage() {
        return maxSearchResultsForPackage;
    }

    public boolean isEnablePackageLifecycleExecutor() {
        return enablePackageLifecycleExecutor;
    }

    public String getDefaultLanguage() {
        return defaultLanguage;
    }

    @Override
    public String toString() {
        return "DocetConfiguration{" + "pathToInstalledDocPackages=" + pathToInstalledDocPackages + ", pathToPages=" + pathToPages + ", pathToImages=" + pathToImages + ", pathToFaq=" + pathToFaq + ", tocFilePath=" + tocFilePath + ", linkToPagePattern=" + linkToPagePattern + ", linkToImagePattern=" + linkToImagePattern + ", linkToFaqPattern=" + linkToFaqPattern + ", docetStaticResAdditionalParams=" + docetStaticResAdditionalParams + ", docetPackageDocsFolderPath=" + docetPackageDocsFolderPath + ", docetPackageSearchIndexFolderPath=" + docetPackageSearchIndexFolderPath + ", version=" + version + ", defaultLanguage=" + defaultLanguage + ", maxSearchResultsForPackage=" + maxSearchResultsForPackage + ", faqTocAtRuntime=" + faqTocAtRuntime + ", previewMode=" + previewMode + ", debugMode=" + debugMode + ", enablePackageLifecycleExecutor=" + enablePackageLifecycleExecutor + '}';
    }

}
