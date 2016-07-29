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

import java.util.Properties;

/**
 * Docet configuration wrapper.
 *
 * @author matteo.casadei
 *
 */
public class DocetConfiguration {

    private final String baseDocetPath;
    private final String searchIndexPath;
    private final String baseTemplateName;
    private final String pathToPages;
    private final String pathToImages;
    private final String pathToFaq;
    private final String tocFilePath;
    private final String faqFilePath;
    private final String mainPageName;
    private final String linkToPagePattern;
    private final String linkToImagePattern;
    private final String linkToFaqPattern;
    private final String docetDivContentId;
    private final String docetDivTocId;
    private final String docetTemplatePath;
    private final String docetStaticResAdditionalParams;
    private final String docetZipPath;
    private final boolean faqTocAtRuntime;
    private final boolean previewMode;

    /**
     * Create a new instance of configuration from a {@link Properties} instance.
     *
     * @param conf the properties instance whereby define the new Docet configuration
     */
    public DocetConfiguration(final Properties conf) {
        this.baseDocetPath = conf.getProperty("docet.base.dir", System.getProperty("user.dir"));
        this.searchIndexPath = conf.getProperty("docet.searchindex.path", null);
        this.baseTemplateName = conf.getProperty("docet.template.name", "default.webpage.html");
        this.docetStaticResAdditionalParams = conf.getProperty("docet.staticresources.additionalParams", null);
        this.pathToPages = conf.getProperty("docet.pages.path", "{0}/pages");
        this.pathToImages = conf.getProperty("docet.images.path", "{0}/imgs");
        this.pathToFaq = conf.getProperty("docet.faq.path", "/{0}/faq");
        this.tocFilePath = conf.getProperty("docet.toc.path", "/{0}/toc.html");
        this.faqFilePath = conf.getProperty("docet.faq.path", "/{0}/faq.html");
        this.mainPageName = conf.getProperty("docet.mainpage.name", "main.html");
        this.linkToPagePattern = conf.getProperty("docet.linktopage.pattern", "../pages/{0}_{1}.mndoc");
        this.linkToFaqPattern = conf.getProperty("docet.linktofaq.pattern", "../faq/{0}_{1}.mndoc");
        this.linkToImagePattern = conf.getProperty("docet.linktoimage.pattern", "../images/{0}_{1}");
        this.docetDivContentId = conf.getProperty("docet.divcontent.id", "docet-content-anchor");
        this.docetDivTocId = conf.getProperty("docet.divtoc.id", "docet-menu-anchor");
        this.previewMode = Boolean.parseBoolean(conf.getProperty("docet.previewmode", "true"));
        this.docetTemplatePath = conf.getProperty("docet.template.path", null);
        this.docetZipPath = conf.getProperty("docet.zip.path", null);
        this.faqTocAtRuntime = Boolean.parseBoolean(conf.getProperty("docet.faq.toc.runtime", "true"));
    }

    public String getBaseDocetPath() {
        return baseDocetPath;
    }

    public String getBaseTemplateName() {
        return baseTemplateName;
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

    public String getMainPageName() {
        return mainPageName;
    }

    public String getLinkToPagePattern() {
        return linkToPagePattern;
    }

    public String getLinkToImagePattern() {
        return linkToImagePattern;
    }

    public String getDocetDivContentId() {
        return docetDivContentId;
    }

    public String getDocetDivTocId() {
        return docetDivTocId;
    }

    public String getSearchIndexPath() {
        return searchIndexPath;
    }

    public boolean isPreviewMode() {
        return previewMode;
    }

    public String getDocetTemplatePath() {
        return docetTemplatePath;
    }

    public String getDocetStaticResAdditionalParams() {
        return docetStaticResAdditionalParams;
    }

    public String getDocetZipPath() {
        return docetZipPath;
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

    public String getFaqFilePath() {
        return faqFilePath;
    }

}
