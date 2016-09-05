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

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import org.apache.lucene.index.IndexNotFoundException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Tag;
import org.jsoup.select.Elements;

import docet.DocetPackageLocator;
import docet.DocetUtils;
import docet.SimplePackageLocator;
import docet.model.DocetDocument;
import docet.model.DocetPackageDescriptor;
import docet.model.DocetPackageNotFoundException;
import docet.model.DocetResponse;
import docet.model.PackageDescriptionResult;
import docet.model.PackageResponse;
import docet.model.PackageSearchResult;
import docet.model.SearchResponse;
import docet.model.SearchResult;

public final class DocetManager {
    
    private static final String IMAGE_DOCET_EXTENSION = ".mnimg";
    private static final String CSS_CLASS_DOCET_MENU = "docet-menu";
    private static final String CSS_CLASS_DOCET_SUBMENU = "docet-menu-submenu";
    private static final String CSS_CLASS_DOCET_MENU_HIDDEN = "docet-menu-hidden";
    private static final String CSS_CLASS_DOCET_MENU_VISIBLE = "docet-menu-visible";
    private static final String CSS_CLASS_DOCET_MENU_HASSUBMENU = "docet-menu-hasmenu";
    private static final String CSS_CLASS_DOCET_MENU_CLOSED = "docet-menu-closed";
    private static final String CSS_CLASS_DOCET_MENU_LINK = "docet-menu-link";
    private static final String CSS_CLASS_DOCET_PAGE_LINK = "docet-page-link";
    private static final String CSS_CLASS_DOCET_FAQ_LINK = "docet-faq-link";
    private static final String CSS_CLASS_DOCET_FAQ_MAINLINK = "docet-faq-mainlink";
    private static final String CSS_CLASS_DOCET_FAQ_LINK_IN_PAGE = "faq-link";
    private static final String ID_DOCET_FAQ_MAIN_LINK = "docet-faq-main-link";
    private static final String ID_DOCET_FAQ_MENU = "docet-faq-menu";
    private static final String DOCET_HTML_ATTR_REFERENCE_LANGUAGE_NAME = "reference-language";
    
    private Document baseDocumentTemplate;
    private Element divContentElement;
    private Element divTocElement;
    private Element divFooterElement;
    private final DocetConfiguration docetConf;
    private final DocetPackageRuntimeManager packageRuntimeManager;
    
    public void start() throws Exception {

        // the following is useful only when using docet in standalone mode:
        // loads a default base html page for doc rendering
        if (docetConf.getDocetTemplatePath() != null) {
            this.baseDocumentTemplate = Jsoup.parse(new File(docetConf.getDocetTemplatePath() + "/" + docetConf.getBaseTemplateName()), "UTF-8");
            divContentElement = baseDocumentTemplate.getElementById(docetConf.getDocetDivContentId());
            divTocElement = baseDocumentTemplate.getElementById(docetConf.getDocetDivTocId());
            this.mergeStaticResSourcesWithAdditionalParams();
        }
        this.packageRuntimeManager.start();
    }

    public void addDocumentationPackage(final String packageName, final Path pathToPackage, final boolean replace) throws Exception {
        final boolean packageAlreadyPresent = this.docetConf.getPathToDocPackage(packageName) != null;
        if (packageAlreadyPresent) {
            if (replace) {
                this.docetConf.addPackage(packageName,  pathToPackage.toString());
            }
        } else {
            this.docetConf.addPackage(packageName,  pathToPackage.toString());
        }
    }

    public void stop() throws Exception {
        this.packageRuntimeManager.stop();
    }

    public DocetManager(final DocetConfiguration docetConf) throws IOException {
        this.docetConf = docetConf;
        this.packageRuntimeManager = new DocetPackageRuntimeManager(new SimplePackageLocator(docetConf), docetConf);
    }

    public DocetManager(final DocetConfiguration docetConf, final DocetPackageLocator packageLocator) throws IOException {
        this.docetConf = docetConf;
        this.packageRuntimeManager = new DocetPackageRuntimeManager(packageLocator, docetConf);
    }

    public DocetPackageRuntimeManager getPackageRuntimeManager() {
        return packageRuntimeManager;
    }

    private void mergeStaticResSourcesWithAdditionalParams() {
        final String additionalParams = this.docetConf.getDocetStaticResAdditionalParams();
        if (additionalParams != null) {
            this.baseDocumentTemplate.getElementsByTag("script").forEach(script -> {
                String src = script.attr("src");
                src += "?" + additionalParams;
                script.attr("src", src);
            });
            this.baseDocumentTemplate.getElementsByTag("link").forEach(link -> {
                String href = link.attr("href");
                href += "?" + additionalParams;
                link.attr("href", href);
            });
        }
    }
    
    private String getPathToPackageDoc(final String packageName) throws Exception {
        return this.packageRuntimeManager.getDocumentDirectoryForPackage(packageName).getAbsolutePath();
    }
    
    public BufferedImage getImageBylangForPackage(final String imgName, final String lang, final String packageName) throws Exception {
        final String basePathToPackage = this.getPathToPackageDoc(packageName);
        
        final String pathToImg;
        if (this.docetConf.isPreviewMode()) {
            final String docetImgsBasePath = basePathToPackage + "/" + MessageFormat.format(this.docetConf.getPathToImages(), lang);
            pathToImg = searchFileInBasePathByName(Paths.get(docetImgsBasePath), imgName).toString();
        } else {
            pathToImg = basePathToPackage + "/" + MessageFormat.format(this.docetConf.getPathToImages(), lang) + "/" + imgName;
        }
        File imgPath = new File(pathToImg);
        return ImageIO.read(imgPath);
    }
    
    public BufferedImage getImageBylang(final String imgName, final String lang) throws Exception {
        return getImageBylangForPackage(imgName, lang, this.docetConf.getDefaultPackageForDebug());
    }
    
    public void getImageBylangForPackage(final String imgName, final String lang, final String packageName, final OutputStream out)
            throws Exception {
        final String basePathToPackage = this.getPathToPackageDoc(packageName);

        final String pathToImg;
        if (this.docetConf.isPreviewMode()) {
            final String docetImgsBasePath = basePathToPackage + "/" + MessageFormat.format(this.docetConf.getPathToImages(), lang);
            final Path imagePath = searchFileInBasePathByName(Paths.get(docetImgsBasePath), imgName);
            if (imagePath == null) {
                throw new IOException("Image " + imgName + " for language " + lang + " not found!");
            } else {
                pathToImg = imagePath.toString();
            }
        } else {
            pathToImg = basePathToPackage + "/" + MessageFormat.format(this.docetConf.getPathToImages(), lang) + "/" + imgName;
        }
        File imgPath = new File(pathToImg);
        try (BufferedInputStream bin = new BufferedInputStream(new FileInputStream(imgPath))) {
            byte[] read = new byte[2048];
            while (bin.available() > 0) {
                bin.read(read);
                out.write(read);
            }
        }
    }
    
    public void getImageBylang(final String imgName, final String lang, final OutputStream out) throws Exception {
        getImageBylangForPackage(imgName, lang, this.docetConf.getDefaultPackageForDebug(), out);
    }

    /**
     * Used to retrieve a specific doc package's main page for a given language,
     * parsing each link within the page so that params are appended to the
     * links.
     *
     * @param lang
     *            the language code the desired main page refers to
     *
     * @param packageName
     *            the target documentation package
     *
     * @param params
     *            the additional params to append to each link found in the page
     *
     * @return the main page in text format
     *
     * @throws IOException
     *             in case of issues on retrieving the main page
     */
    public String serveMainPageForPackage(final String lang, final String packageName, final Map<String, String[]> params) throws Exception {
        divTocElement.html(parseTocForPackage(packageName, lang, params).body().getElementsByTag("nav").first().html());
        final StringBuilder html = new StringBuilder(parseMainPageForPackage(lang, packageName, params).body().getElementsByTag("div").first().html());
        html.append(generateFooter(lang, packageName, "main"));
//        html.append("<script type=\"text/javascript\">var language='" + lang + "';\n"
//                + "var installedPackages = "
//                + this.packageRuntimeManager.getInstalledPackages().stream().map(name -> "'" + name + "'").collect(Collectors.toList())
//                + ";\n"
//                + "docet.enabledPackages = installedPackages;</script>");
        divContentElement.html(html.toString());
        return baseDocumentTemplate.html();
    }

    /**
     * Used to retrieve the TOC for a given language, parsing each link in the
     * TOC so as to have params appended to it.
     *
     * @param packageName
     *            the name of target documentation package
     *
     * @param lang
     *            the language code the desired TOC refers to
     *
     * @param params
     *            the additional params to append to each link making up TOC
     *
     * @return the TOC in text format
     *
     * @throws IOException
     *             in case of issues on retrieving the TOC page
     */
    public String serveTableOfContentsForPackage(final String packageName, final String lang, final Map<String, String[]> params) throws Exception {
        return parseTocForPackage(packageName, lang, params).body().getElementsByTag("nav").first().html();
    }

    public String serveTableOfContents(final String lang, final Map<String, String[]> params) throws Exception {
        return this.serveTableOfContentsForPackage(this.docetConf.getDefaultPackageForDebug(), lang, params);
    }
    
    private Document parseMainPageForPackage(final String lang, final String packageName, final Map<String, String[]> params) throws Exception {
        final String basePathToPackage = this.getPathToPackageDoc(packageName);
        
        final Document docPage = Jsoup.parseBodyFragment(new String(DocetUtils.fastReadFile(
                new File(basePathToPackage + "/" + MessageFormat.format(docetConf.getPathToPages(), lang) + "/" + this.docetConf.getMainPageName())
                        .toPath()),
                "UTF-8"), "UTF-8");
        final Elements imgs = docPage.getElementsByTag("img");
        imgs.stream().forEach(img -> {
            parseImage(packageName, img, lang, params);
        });
        final Elements anchors = docPage.getElementsByTag("a");
        anchors.stream().filter(a -> {
            final String href = a.attr("href");
            return !href.startsWith("#") && !href.startsWith("http://") && !href.startsWith("https://");
        }).forEach(a -> {
            parseAnchorItemInPage(packageName, a, lang, params);
        });
        return docPage;
    }

    /**
     * Retrieve a page given a page id and a reference language. Links in the
     * page content are parsed and provided params appended to them.
     *
     * @param packageName
     *            the target documentation package
     *
     * @param pageId
     *            the id of the page to be served
     * @param lang
     *            the reference language for this page's id
     * @param faq
     *            true if the page to be served is a faq page, false in case of
     *            a "standard" documentation page
     * @param params
     *            the additional params to be appended to each link in the page
     *
     * @return the text representation of the requested page
     *
     * @throws IOException
     *             in case parsing of the page got issues
     */
    public String servePageIdForLanguageForPackage(final String packageName, final String pageId, final String lang, final boolean faq, final Map<String, String[]> params)
            throws Exception {
        final StringBuilder html = new StringBuilder(parsePageForPackage(packageName, pageId, lang, faq, params).body().getElementsByTag("div").first().html());
        html.append(generateFooter(lang, packageName, pageId));
        return html.toString();
    }

    public String servePageIdForLanguage(final String pageId, final String lang, final boolean faq, final Map<String, String[]> params)
            throws Exception {
        return this.servePageIdForLanguageForPackage(this.docetConf.getDefaultPackageForDebug(), pageId, lang, faq, params);
    }

    private Document parsePageForPackage(final String packageName, final String pageId, final String lang, final boolean faq, final Map<String, String[]> params)
            throws Exception {
        final Document docPage = this.loadPageByIdForPackageAndLanguage(packageName, pageId, lang, faq);
        final Elements imgs = docPage.getElementsByTag("img");
        imgs.stream().forEach(img -> {
            parseImage(packageName, img, lang, params);
        });
        final Elements anchors = docPage.getElementsByTag("a");
        anchors.stream().filter(a -> {
            final String href = a.attr("href");
            return !href.startsWith("#") && !href.startsWith("http://") && !href.startsWith("https://");
        }).forEach(a -> {
            parseAnchorItemInPage(packageName, a, lang, params);
        });
        return docPage;
    }

    private String generateFooter(final String lang, final String packageId, final String pageId) {
        String res = "";
        res += "<div class='docet-footer'><p>";
        res += "<span style='visibility:hidden;display:none;' id='docet-page-id'><b>Page id:</b> " + packageId + ":" + pageId + "</span>";
        if (docetConf.isDebugMode()) {
            res += "<b>Version</b>: " + docetConf.getVersion() + " | <b>Language:</b> " + lang;
        }
        res += "</p></div>";
        return res;
    }

    private Document loadPageByIdForPackageAndLanguage(final String packageName, final String pageId, final String lang, final boolean faq)
            throws Exception {
        final String pathToPage;
        if (faq) {
            pathToPage = this.getFaqPathByIdForPackageAndLanguage(packageName, pageId, lang);
        } else {
            pathToPage = this.getPagePathByIdForPackageAndLanguage(packageName, pageId, lang);
        }
        return Jsoup.parseBodyFragment(new String(DocetUtils.fastReadFile(new File(pathToPage).toPath()), "UTF-8"));
    }
    
    private String getFaqPathByIdForPackageAndLanguage(final String packageName, final String faqId, final String lang) throws Exception {
        final String basePath = this.getPathToPackageDoc(packageName);
        final String pathToFaq = basePath + "/" + MessageFormat.format(docetConf.getPathToFaq(), lang) + "/" + faqId + ".html";
        return pathToFaq;
    }
    
    private String getPagePathByIdForPackageAndLanguage(final String packageName, final String pageId, final String lang) throws Exception {
        final String basePath = this.getPathToPackageDoc(packageName);

        final String pathToPage;
        if (this.docetConf.isPreviewMode()) {
            final String docetDocsBasePath = basePath + "/" + MessageFormat.format(docetConf.getPathToPages(), lang);
            final Path pagePath = searchFileInBasePathByName(Paths.get(docetDocsBasePath), pageId + ".html");
            if (pagePath == null) {
                throw new IOException("Page " + pageId + " for language " + lang + " not found!");
            } else {
                pathToPage = searchFileInBasePathByName(Paths.get(docetDocsBasePath), pageId + ".html").toString();
            }
        } else {
            pathToPage = basePath + "/" + MessageFormat.format(docetConf.getPathToPages(), lang) + "/" + pageId + ".html";
        }
        return pathToPage;
    }
    
    private static Path searchFileInBasePathByName(final Path basePath, final String fileName) throws IOException {
        final Holder<Path> result = new Holder<Path>();
        Files.walkFileTree(basePath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                if (file.endsWith(fileName)) {
                    result.setValue(file);
                    return FileVisitResult.TERMINATE;
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return result.value;
    }
    
    private Document loadTocForPackage(final String packageName, final String lang) throws Exception {
        final String basePath = this.getPathToPackageDoc(packageName);
        return Jsoup
                .parseBodyFragment(new String(
                        DocetUtils.fastReadFile(
                                new File(basePath + MessageFormat.format(this.docetConf.getTocFilePath(), lang)).toPath()),
                        "UTF-8"), "UTF-8");
    }

    private Document parseTocForPackage(final String packageName, final String lang, final Map<String, String[]> params) throws Exception {
        final Document docToc = loadTocForPackage(packageName, lang);
        
        // inject default docet menu css class on main menu
        docToc.select("nav > ul").addClass(CSS_CLASS_DOCET_MENU);
        docToc.select("nav > ul").attr("package",packageName);
        docToc.select("nav > ul").addClass(CSS_CLASS_DOCET_MENU_VISIBLE);
        docToc.select("nav > ul > li").addClass(CSS_CLASS_DOCET_MENU);
        
        if (this.docetConf.isFaqTocAtRuntime()) {
            injectFaqItemsInTOC(docToc, lang);
        }
        
        final Elements anchors = docToc.getElementsByTag("a");
        anchors.stream().forEach(a -> {
            parseTOCItem(packageName, a, lang, params);
        });
        final Elements lis = docToc.getElementsByTag("li");
        lis.stream().forEach(li -> {
            injectClasses(li);
        });
        return docToc;
    }
    
    private static final String getFaqPath() {
        return "<a class=\"" + CSS_CLASS_DOCET_FAQ_LINK + "\" id=\"" + ID_DOCET_FAQ_MAIN_LINK + "\" href=\"faq.html\">FAQ</a>";
    }

    private SearchResult convertDocetDocumentToSearchResult(final String lang, final String packageId,
            final Map<String, String[]> additionalParams, final Document toc, final DocetDocument doc) {
        final int docType = doc.getType();
        final String pageLink;
        final String pageId;
        final String[] breadCrumbs;
        switch (docType) {
            case DocetDocument.DOCTYPE_FAQ:
                pageLink = MessageFormat.format(this.docetConf.getLinkToFaqPattern(), packageId, doc.getId(), lang);
                pageId = "faq_" + doc.getId() + "_" + lang;
                breadCrumbs = new String[] { getFaqPath() };
                break;
            case DocetDocument.DOCTYPE_PAGE:
                pageLink = MessageFormat.format(this.docetConf.getLinkToPagePattern(), packageId, doc.getId(), lang);
                pageId = doc.getId() + "_" + lang;
                breadCrumbs = createBreadcrumbsForPageFromToc(packageId, pageId, toc);
                break;
            default:
                throw new IllegalArgumentException("Unsupported document type " + docType);
        }
        return SearchResult.toSearchResult(packageId, doc, pageId, appendParamsToUrl(pageLink, additionalParams), breadCrumbs);
    }

    private void injectFaqItemsInTOC(final Document toc, final String lang) throws IOException {
        final Element faqList = toc.getElementById(ID_DOCET_FAQ_MENU);
        if (faqList == null) {
            return;
        }
        final Element faqMainLink = toc.getElementById(ID_DOCET_FAQ_MAIN_LINK);
        if (faqMainLink == null) {
            return;
        } else {
            faqMainLink.addClass(CSS_CLASS_DOCET_FAQ_MAINLINK);
        }
        
        final Holder<Integer> countFaqs = new Holder<Integer>();
        countFaqs.setValue(0);
        faqList.select("a").forEach(faqA -> {
            faqA.addClass(CSS_CLASS_DOCET_FAQ_LINK);
            countFaqs.setValue(countFaqs.getValue() + 1);
        });
        
        if (countFaqs.getValue() > 0) {
            toc.select("nav > ul > li").addClass(CSS_CLASS_DOCET_MENU_HASSUBMENU);
        }
    }
    
    private void injectClasses(final Element item) {
        if (!item.hasClass(CSS_CLASS_DOCET_MENU)) {
            item.addClass(CSS_CLASS_DOCET_SUBMENU);
        }
        final Elements subItems = item.getElementsByTag("ul");
        subItems.stream().peek(ul -> {
            ul.addClass(CSS_CLASS_DOCET_SUBMENU);
            ul.addClass(CSS_CLASS_DOCET_MENU_HIDDEN);
        }).count();
        // add an enclosing div for each anchor within a li
        final Element a = item.children().select("a").get(0);
        item.prependChild(new Element(Tag.valueOf("div"), ""));
        item.select("div").get(0).append(a.outerHtml());
        final Element appendedA = item.select("div").get(0).select("a").get(0);
        a.remove();
        if (!appendedA.attr("id").equals(ID_DOCET_FAQ_MAIN_LINK) && appendedA.hasClass(CSS_CLASS_DOCET_FAQ_LINK)) {
            return;
        }
        // if this li (item) has child then we must be confident it has a
        // submenu
        if (!item.children().select("ul").isEmpty()) {
            item.addClass(CSS_CLASS_DOCET_MENU_HASSUBMENU);
            item.select("div").get(0).addClass(CSS_CLASS_DOCET_MENU_CLOSED);
        }
    }
    
    private void parseImage(final String packageName, final Element item, final String lang, final Map<String, String[]> params) {
        final String[] imgPathTokens = item.attr("src").split("/");
        final String imgName = imgPathTokens[imgPathTokens.length - 1];
        final String imgNameNormalizedExtension = imgName + IMAGE_DOCET_EXTENSION;
        String href = MessageFormat.format(this.docetConf.getLinkToImagePattern(), packageName, lang, imgNameNormalizedExtension);
        href = appendParamsToUrl(href, params);
        item.attr("src", href);
    }
    
    private void parseTOCItem(final String packageName, final Element item, String lang, final Map<String, String[]> params) {
        final String barePagename = item.attr("href").split(".html")[0];
        // check if the linked document is written in another language!
        final String referenceLanguage = item.attr(DOCET_HTML_ATTR_REFERENCE_LANGUAGE_NAME);
        if (!referenceLanguage.isEmpty()) {
            lang = referenceLanguage;
        }
        
        String href;
        if (item.hasClass(CSS_CLASS_DOCET_FAQ_LINK)) {
            href = MessageFormat.format(this.docetConf.getLinkToFaqPattern(), packageName, barePagename, lang);
            // determine page id: if page name is samplepage_it.html
            // then id will be simply samplepage_it
            if (!item.attr("id").equals(ID_DOCET_FAQ_MAIN_LINK)) {
                item.attr("id", "faq_" + barePagename + "_" + lang);
            }
        } else {
            href = MessageFormat.format(this.docetConf.getLinkToPagePattern(), packageName, barePagename, lang);
            // determine page id: if page name is samplepage_it.html
            // then id will be simply samplepage_it
            item.attr("id", barePagename + "_" + lang);
            item.attr("title", item.text());
        }
        href = appendParamsToUrl(href, params);
        item.addClass(CSS_CLASS_DOCET_MENU_LINK);
        item.attr("href", href);
        item.attr("package", packageName);
    }
    
    private void parseAnchorItemInPage(final String packageName, final Element item, final String lang, final Map<String, String[]> params) {
        final String crossPackageId = item.attr("package");
        final String[] pageNameTokens = item.attr("href").split(".html");
        final String barePagename = pageNameTokens[0];
        final String fragment;
        if (pageNameTokens.length == 2) {
            fragment = pageNameTokens[1];
        } else {
            fragment = "";
        }
        final String linkId;
        String href;
        final String ultimatePackageId;
        if (crossPackageId.isEmpty()) {
            ultimatePackageId = packageName;
        } else {
            ultimatePackageId = crossPackageId;
        }
        if (item.hasClass(CSS_CLASS_DOCET_FAQ_LINK_IN_PAGE)) {
            href = MessageFormat.format(this.docetConf.getLinkToFaqPattern(), ultimatePackageId, barePagename, lang) + fragment;
            linkId = "faq_" + barePagename + "_" + lang;
            item.removeClass(CSS_CLASS_DOCET_FAQ_LINK_IN_PAGE);
        } else {
            href = MessageFormat.format(this.docetConf.getLinkToPagePattern(), ultimatePackageId, barePagename, lang) + fragment;
            linkId = barePagename + "_" + lang;
        }
        href = appendParamsToUrl(href, params);
        // determine page id: if page name is samplepage_it.html
        // then id will be simply samplepage_it
        item.attr("id", linkId);
        item.attr("href", href);
        item.attr("package", ultimatePackageId);
        item.addClass(CSS_CLASS_DOCET_PAGE_LINK);
    }
    
    private String appendParamsToUrl(final String url, final Map<String, String[]> params) {
        final String parsedUrl;
        if (params.isEmpty()) {
            parsedUrl = url;
        } else {
            final Holder<String> tmpUrl = new Holder<String>();
            tmpUrl.setValue(url + "?");
            params.entrySet().stream().forEach(entry -> {
                tmpUrl.setValue(tmpUrl.getValue() + entry.getKey() + "=" + entry.getValue()[0] + "&");
            });
            final String tmpUrlValue = tmpUrl.getValue();
            parsedUrl = tmpUrlValue.substring(0, tmpUrlValue.lastIndexOf("&"));
        }
        return parsedUrl;
    }

    private String getLinkToPackageMainPage(final String packageId, final String lang, final Map<String, String[]> additionalParams) {
        return this.appendParamsToUrl(
                MessageFormat.format(this.docetConf.getLinkToPagePattern(), packageId, "main", lang),
                additionalParams);
    }
    public PackageResponse servePackageDescriptionForLanguage(final String[] packagesId, final String lang, final Map<String, String[]> additionalParams) {
        PackageResponse packageResponse;
        final List<PackageDescriptionResult> results = new ArrayList<>();
        try {
            for (final String packageId : packagesId) {
                final String pathToPackage = this.getPathToPackageDoc(packageId);
                final String packageLink = getLinkToPackageMainPage(packageId, lang, additionalParams);
                try {
                    final Document descriptor = Jsoup.parseBodyFragment(
                            new String(DocetUtils.fastReadFile(new File(pathToPackage).toPath().resolve("descriptor.html")), "UTF-8"));
                    final Elements divDescriptor = descriptor.select("div[lang=" + lang + "]");
                    if (divDescriptor.isEmpty()) {
                        packageResponse = new PackageResponse(DocetResponse.STATUS_CODE_FAILURE, "Descriptor not found");
                        return packageResponse;
                    } else {
                        final String title = divDescriptor.select("h1").get(0).text(); 
                        final String desc = divDescriptor.select("p").get(0).text();
                        final String imageIcoPath = new File("docet").toPath().resolve("doc-default.png").toString();
                        final PackageDescriptionResult res = new PackageDescriptionResult(title, packageId, packageLink, desc, imageIcoPath, lang);
                        results.add(res);
                    }
                } catch (IOException ex) {
                    results.add(new PackageDescriptionResult(packageId, packageId, packageLink, packageId, new File("docet").toPath().resolve("doc-default.png").toString(), lang));
                }
            }
            packageResponse = new PackageResponse();
            packageResponse.addItems(results);
        } catch (Exception e) {
            e.printStackTrace();
            packageResponse = new PackageResponse(DocetResponse.STATUS_CODE_FAILURE, e.getMessage());
        }
        return packageResponse;
    }

    public SearchResponse searchPagesByKeywordAndLangWithRerencePackage(final String searchText, final String lang,
            final String sourcePackageName, final Set<String> enabledPackages, final Map<String, String[]> additionalParams) {
        SearchResponse searchResponse;
        
        final List<PackageSearchResult> results = new ArrayList<>();
        final Holder<PackageSearchResult> packageResForCurrentPackage = new Holder<>();
        try {
            final Map<String, List<SearchResult>> docsForPackage = new HashMap<>();
            final String[] exactSearchTokens = DocetUtils.parsePageIdSearchToTokens(searchText);
            //choose search type: search for a speficif doc rather than do an extensive search on a set of given packages
            if (exactSearchTokens.length == 2) {
                final String packageid = exactSearchTokens[0];
                final String pageid = exactSearchTokens[1];
                final DocetDocumentSearcher packageSearcher = this.packageRuntimeManager.getSearchIndexForPackage(packageid);
                final DocetDocument foundDoc = packageSearcher.searchDocumentById(pageid, lang);
                final List<SearchResult> packageSearchRes = new ArrayList<>();
                if (foundDoc != null) {
                    final Document toc = parseTocForPackage(packageid, lang, additionalParams);
                    packageSearchRes.add(this.convertDocetDocumentToSearchResult(lang, packageid, additionalParams, toc, foundDoc));
                }
                docsForPackage.put(packageid, packageSearchRes);
            } else {
                for (final String packageId : enabledPackages) {
                    final List<DocetDocument> docs = new ArrayList<>();
                    final List<SearchResult> packageSearchRes = new ArrayList<>();
                    try {
                        final DocetDocumentSearcher packageSearcher = this.packageRuntimeManager.getSearchIndexForPackage(packageId);
                        docs.addAll(packageSearcher.searchForMatchingDocuments(searchText, lang, this.docetConf.getMaxSearchResultsForPackage()));
                        final Document toc = parseTocForPackage(packageId, lang, additionalParams);
                        docs.stream().sorted((d1, d2) -> d2.getRelevance() - d1.getRelevance()).forEach(e -> {
                            final SearchResult searchRes = this.convertDocetDocumentToSearchResult(lang, packageId, additionalParams, toc, e);
                            packageSearchRes.add(searchRes);
                        });
                        docsForPackage.put(packageId, packageSearchRes);
                    } catch (IndexNotFoundException ex) {
                        //TODO do something about it
                    }
                }
            }
            docsForPackage.entrySet().stream().forEach(entry -> {
                final String packageid = entry.getKey();
                final List<SearchResult> searchRes = entry.getValue();
                String packageName;
                final String packageLink = getLinkToPackageMainPage(packageid, lang, additionalParams);
                try {
                    final DocetPackageDescriptor desc = this.packageRuntimeManager.getDescriptorForPackage(packageid);
                    packageName = desc.getLabelForLang(lang);
                } catch (DocetPackageNotFoundException ex) {
                    packageName = packageid;
                }
                if (packageName == null) {
                    packageName = packageid;
                }
                final PackageSearchResult packageRes = PackageSearchResult.toPackageSearchResult(packageid, packageName, packageLink, searchRes);
                if (packageid.equals(sourcePackageName)) {
                    packageResForCurrentPackage.setValue(packageRes);
                } else {
                    results.add(packageRes);
                }
            });
            searchResponse = new SearchResponse(sourcePackageName);
            searchResponse.addResults(results);
            if (packageResForCurrentPackage.value != null) {
                searchResponse.setCurrentPackageResults(packageResForCurrentPackage.value);
            }
        } catch (Exception e) {
            e.printStackTrace();
            searchResponse = new SearchResponse(sourcePackageName, SearchResponse.STATUS_CODE_FAILURE, e.getMessage());
        }
        return searchResponse;
    }

    private String[] createBreadcrumbsForPageFromToc(final String packageId, final String pageId, final Document toc) {
        List<String> crumbs = new ArrayList<>();
        Elements pageLinks = toc.getElementsByTag("a");
        Optional<Element> pageLink = pageLinks.stream().filter(link -> link.attr("id").trim().equals(pageId)).findFirst();
        if (pageLink.isPresent()) {
            final Element tocLink = pageLink.get();
            Element parent = null;
            Element parentUl = tocLink.parent().parent().parent();
            parent = parentUl.parent();
            while (parent != null && parent.tagName().toLowerCase().equals("li")) {
                Element anchorToAdd = parent.getElementsByTag("div").get(0).getElementsByTag("a").get(0);
                anchorToAdd.attr("package", packageId);
                crumbs.add(anchorToAdd.outerHtml());
                // possibly a ul
                Element ul = parent.parent();
                if (ul == null) {
                    parent = null;
                } else {
                    parent = ul.parent();
                }
            }
            // while (!crumbs.isEmpty()) {
            // breadcrumb += crumbs.remove(crumbs.size() - 1) + " > ";
            // }
            // if (breadcrumb.endsWith(" > ")) {
            // breadcrumb = breadcrumb.substring(0, breadcrumb.lastIndexOf(" >
            // "));
            // }
        }
        return crumbs.toArray(new String[] {});
        // return breadcrumb;
    }
    
    private static class Holder<T> {
        private T value;
        
        Holder() {
        }
        
        T getValue() {
            return value;
        }
        
        void setValue(T value) {
            this.value = value;
        }
    }
}
