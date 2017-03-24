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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jackson.map.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities;
import org.jsoup.parser.Parser;
import org.jsoup.parser.Tag;
import org.jsoup.select.Elements;

import docet.DocetDocumentGenerator;
import docet.DocetExecutionContext;
import docet.DocetPackageLocator;
import docet.DocetUtils;
import docet.SimplePackageLocator;
import docet.StatsCollector;
import docet.error.DocetDocumentParsingException;
import docet.error.DocetDocumentSearchException;
import docet.error.DocetException;
import docet.error.DocetPackageException;
import docet.error.DocetPackageNotFoundException;
import docet.model.DocetDocument;
import docet.model.DocetPackageDescriptor;
import docet.model.DocetPage;
import docet.model.PackageDescriptionResult;
import docet.model.PackageResponse;
import docet.model.PackageSearchResult;
import docet.model.SearchResponse;
import docet.model.SearchResult;

public final class DocetManager {

    private static final Logger LOGGER = Logger.getLogger(DocetManager.class.getName());
    private static final String IMAGE_DOCET_EXTENSION = ".mnimg";
    private static final String CSS_CLASS_DOCET_MENU = "docet-menu";
    private static final String CSS_CLASS_DOCET_SUBMENU = "docet-menu-submenu";
    private static final String CSS_CLASS_DOCET_MENU_HIDDEN = "docet-menu-hidden";
    private static final String CSS_CLASS_DOCET_MENU_VISIBLE = "docet-menu-visible";
    private static final String CSS_CLASS_DOCET_MENU_HASSUBMENU = "docet-menu-hasmenu";
    private static final String CSS_CLASS_DOCET_MENU_CLOSED = "docet-menu-closed";
    private static final String CSS_CLASS_DOCET_MENU_LINK = "docet-menu-link";
    private static final String CSS_CLASS_DOCET_PAGE_LINK = "docet-page-link";
    private static final String CSS_CLASS_DOCET_PDF_LINK = "docet-pdf-link";
    private static final String CSS_CLASS_DOCET_FAQ_LINK = "docet-faq-link";
    private static final String CSS_CLASS_DOCET_FAQ_MAINLINK = "docet-faq-mainlink";
    private static final String CSS_CLASS_DOCET_FAQ_LINK_IN_PAGE = "faq-link";
    private static final String ID_DOCET_FAQ_MAIN_LINK = "docet-faq-main-link";
    private static final String ID_DOCET_FAQ_MENU = "docet-faq-menu";
    private static final String DOCET_HTML_ATTR_REFERENCE_LANGUAGE_NAME = "reference-language";

    private static final String ENCODING_UTF_8 = "UTF-8";
    private static final String EXTENSION_HTML = ".html";
    private static final String EXTENSION_PDF = ".pdf";
    private static final String DOM_PATH_TO_TOC_LIST = "nav > ul";

    private static final String DOCET_ATTR_PACKAGE = "package";
    private static final String DOCET_ATTR_DOCETREF = "docetref";

    private static final String URL_PATTERN = "^(/package)|(/search)|(/toc)|"
        + "(/main/[a-zA-Z_0-9\\-]+/index.mndoc)|"
        + "(/faq/[a-zA-Z_0-9\\-]+/[a-zA-Z_0-9\\-]+\\.mndoc)|"
        + "(/pages/[a-zA-Z_0-9\\-]+/[a-zA-Z_0-9\\-]+\\.mndoc)|"
        + "(/pages/[a-zA-Z_0-9\\-]+/[a-zA-Z_0-9\\-]+\\.pdf)|"
        + "(/pdfs/[a-zA-Z_0-9\\-]+/[a-zA-Z_0-9\\-]+\\.pdf)|"
        + "(/icons/[a-zA-Z_0-9\\-]+)|"
        + "(/images/[a-zA-Z_0-9\\-]+/[a-zA-Z_0-9\\-]+\\.\\w{3,}\\.mnimg)";

    public static final String STATS_DETAILS_PACKAGE_ID = "package_id";
    public static final String STATS_DETAILS_PAGE_ID = "page_id";
    public static final String STATS_DETAILS_SEARCH_TERM = "search_term";
    public static final String STATS_DETAILS_SEARCH_SOURCE_PACKAGE = "search_source_package";
    public static final String STATS_DETAILS_LANGUAGE = "language";

    private final DocetConfiguration docetConf;
    private final DocetPackageRuntimeManager packageRuntimeManager;
    private final DocetDocumentParserFactory parserFactory;
    private DocetDocumentGenerator documentGenerator; 

    /**
     * Adopted only in DOCet standalone mode.
     *
     * @param docetConf
     * @throws IOException
     */
    public DocetManager(final DocetConfiguration docetConf) throws DocetException {
        this.docetConf = docetConf;
        this.parserFactory = new DocetDocumentParserFactory();
        this.documentGenerator = new SimpleDocetPdfDocGenerator(
            this.parserFactory.getParserForFormat(DocetDocFormat.TYPE_PDF), this);
        try {
            this.packageRuntimeManager = new DocetPackageRuntimeManager(new SimplePackageLocator(docetConf), docetConf);
        } catch (IOException e) {
            throw new DocetException(DocetException.CODE_GENERIC_ERROR, "Initializaton of package runtime manager failed", e);
        }
    }

    public DocetManager(final DocetConfiguration docetConf, final DocetPackageLocator packageLocator) {
        this.docetConf = docetConf;
        this.packageRuntimeManager = new DocetPackageRuntimeManager(packageLocator, docetConf);
        this.parserFactory = new DocetDocumentParserFactory();
    }

    public void start() throws IOException {
        this.packageRuntimeManager.start();
    }

    public void stop() throws InterruptedException {
        this.packageRuntimeManager.stop();
    }

    private String getPathToPackageDoc(final String packageName, final DocetExecutionContext ctx) throws DocetPackageException {
        return this.packageRuntimeManager.getDocumentDirectoryForPackage(packageName, ctx).getAbsolutePath();
    }

    private void getImageBylangForPackage(final String imgName, final String lang, final String packageName,
        final OutputStream out, final DocetExecutionContext ctx)
        throws DocetException {
        try {
            final String basePathToPackage = this.getPathToPackageDoc(packageName, ctx);

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
                while (bin.available() > 0 && bin.read(read) > 0) {
                    out.write(read);
                }
            }
        } catch (IOException ex) {
            throw new DocetException(
                DocetException.CODE_RESOURCE_NOTFOUND, "Error on loading image '" + imgName + "' package " + packageName, ex);
        } catch (DocetPackageException ex) {
            this.handleDocetPackageException(ex, packageName);
        }
    }

    private void getIconForPackage(final String packageName, final OutputStream out, final DocetExecutionContext ctx)
        throws DocetException {
        try {
            final String basePathToPackage = this.getPathToPackageDoc(packageName, ctx);
            final String pathToIcon = basePathToPackage + "/icon.png";
            File imgPath = new File(pathToIcon);
            try (BufferedInputStream bin = new BufferedInputStream(new FileInputStream(imgPath))) {
                byte[] read = new byte[2048];
                while (bin.available() > 0 && bin.read(read) > 0) {
                    out.write(read);
                }
            }
        } catch (IOException ex) {
            throw new DocetException(
                DocetException.CODE_RESOURCE_NOTFOUND, "Error on loading package icon for package " + packageName, ex);
        } catch (DocetPackageException ex) {
            this.handleDocetPackageException(ex, packageName);
        }
    }

    byte[] getIconForPackage(final String packageName, final DocetExecutionContext ctx)
        throws DocetException, IOException {
        String basePathToPackage = "";
        try {
            basePathToPackage = this.getPathToPackageDoc(packageName, ctx);
        } catch (DocetPackageException e) {
            handleDocetPackageException(e, packageName);
        }
        final String pathToIcon = basePathToPackage + "/icon.png";
        File imgPath = new File(pathToIcon);
        return Files.readAllBytes(imgPath.toPath());
    }

    private void handleDocetPackageException(final DocetPackageException pkgEx, final String packageid)
        throws DocetException {
        final DocetException res;
        final String msg = pkgEx.getMessage();
        if (msg.equals(DocetPackageException.MSG_ACCESS_DENIED)) {
            res = new DocetException(
                DocetException.CODE_PACKAGE_ACCESS_DENIED, "Access denied for package " + packageid, pkgEx);
        } else {
            res = new DocetException(
                DocetException.CODE_PACKAGE_NOTFOUND, "Package not found " + packageid, pkgEx);
        }
        throw res;
    }

    /**
     * Used to retrieve the TOC for a given language, parsing each link in the TOC so as to have params appended to it.
     *
     * @param packageName the name of target documentation package
     *
     * @param lang the language code the desired TOC refers to
     *
     * @param params the additional params to append to each link making up TOC
     *
     * @return the TOC in text format
     * @throws DocetPackageNotFoundException
     * @throws UnsupportedEncodingException
     *
     * @throws IOException in case of issues on retrieving the TOC page
     */
    private String serveTableOfContentsForPackage(final String packageName, final String lang,
        final Map<String, String[]> params, final DocetExecutionContext ctx)
        throws DocetException {
        String html = "";
        try {
            html = parseTocForPackage(packageName, lang, params, ctx).body().getElementsByTag("nav").first().html();
        } catch (IOException ex) {
            throw new DocetException(
                DocetException.CODE_RESOURCE_NOTFOUND, "Error on retrieving TOC for package '" + packageName + "'", ex);
        } catch (DocetPackageException ex) {
            this.handleDocetPackageException(ex, packageName);
        }
        return DocetUtils.cleanPageText(html);
    }

    /**
     * Retrieve a page given a page id and a reference language. Links in the page content are parsed and provided
     * params appended to them.
     *
     * @param packageName the target documentation package
     *
     * @param pageId the id of the page to be served
     * @param lang the reference language for this page's id
     * @param faq true if the page to be served is a faq page, false in case of a "standard" documentation page
     * @param params the additional params to be appended to each link in the page
     *
     * @return the text representation of the requested page
     * @throws DocetPackageNotFoundException
     *
     * @throws IOException in case parsing of the page got issues
     */
    String servePageIdForLanguageForPackage(final String packageName, final String pageId, final String lang,
        final DocetDocFormat format, final boolean faq, final Map<String, String[]> params, final DocetExecutionContext ctx)
        throws DocetException {
        final StringBuilder html = new StringBuilder();
        String res = "";
        try {
            final Document htmlDoc = parsePageForPackage(packageName, pageId, lang, format, faq, params, ctx);
            if (format == DocetDocFormat.TYPE_HTML) {
                html.append(htmlDoc.body().getElementsByTag("div").first().html());
                html.append(generateFooter(lang, packageName, pageId));
                res = DocetUtils.cleanPageText(html.toString());
            } else if (format == DocetDocFormat.TYPE_PDF) {
                htmlDoc.outputSettings().prettyPrint(false);
                html.append(htmlDoc.html());
                res = DocetUtils.cleanPageText(html.toString());
            } else {
                throw new DocetException(DocetException.CODE_GENERIC_ERROR, "Page format not supported for page " + pageId + " package " + packageName);
            }
        } catch (IOException ex) {
            throw new DocetException(DocetException.CODE_RESOURCE_NOTFOUND, "Error on retrieving page '" + pageId + "' for package '" + packageName + "'", ex);
        } catch (DocetPackageException ex) {
            this.handleDocetPackageException(ex, packageName);
        }
        return res;
    }

    private Document parsePageForPackage(final String packageName, final String pageId, final String lang,
        final DocetDocFormat format, final boolean faq, final Map<String, String[]> params, final DocetExecutionContext ctx)
        throws DocetPackageException, IOException {
        final Document docPage = this.loadPageByIdForPackageAndLanguage(packageName, pageId, lang, format, faq, ctx);

        final Elements imgs = docPage.getElementsByTag("img");
        for (Element img: imgs) {
            parseImage(packageName, img, lang, format, params, ctx);
        }
        final Elements anchors = docPage.getElementsByTag("a");
        anchors.stream().filter(a -> {
            final String href = a.attr("href");
            return !href.startsWith("http://") && !href.startsWith("https://");
        }).forEach(a -> {
            parseAnchorItemInPage(packageName, a, lang, params);
        });
        return docPage;
    }

    private String generateFooter(final String lang, final String packageId, final String pageId) {
        String res = "";
        res += "<div class='docet-page-info docet-page-info-hidden'>" + packageId + ":" + pageId + "</div>";
        if (docetConf.isDebugMode()) {
            res += "<div class='docet-debug-info'>";
            final String debugInfo = "Docet " + docetConf.getVersion() + " | Language: " + lang;
            res += debugInfo + "</div>";
        }
        return res;
    }

    private String parseLanguageForPossibleFallback(final String packageId, final String lang,
        final DocetExecutionContext ctx) throws DocetPackageException {
        final DocetPackageDescriptor desc = this.packageRuntimeManager.getDescriptorForPackage(packageId, ctx);
        final String fallbackLang = desc.getFallbackLangForLang(lang);
        if (fallbackLang == null) {
            return lang;
        } else {
            return fallbackLang;
        }
    }

    private Document loadPageByIdForPackageAndLanguage(final String packageName, final String pageId, final String lang,
        final DocetDocFormat format, final boolean faq, final DocetExecutionContext ctx)
        throws DocetPackageException, IOException {
        final String pathToPage;
        final String actuaLang = this.parseLanguageForPossibleFallback(packageName, lang, ctx);
        if (faq) {
            pathToPage = this.getFaqPathByIdForPackageAndLanguage(packageName, pageId, actuaLang, ctx);
        } else {
            pathToPage = this.getPagePathByIdForPackageAndLanguage(packageName, pageId, actuaLang, ctx);
        }
        switch (format) {
            case TYPE_PDF :
                return Jsoup.parse(new String(DocetUtils.fastReadFile(new File(pathToPage).toPath()), ENCODING_UTF_8), "", Parser.xmlParser());
            case TYPE_HTML:
            default:
                return Jsoup
                    .parseBodyFragment(new String(DocetUtils.fastReadFile(new File(pathToPage).toPath()), ENCODING_UTF_8));
        }
    }

    private String getFaqPathByIdForPackageAndLanguage(final String packageName, final String faqId, final String lang,
        final DocetExecutionContext ctx) throws DocetPackageException {
        final String basePath = this.getPathToPackageDoc(packageName, ctx);
        return basePath + "/" + MessageFormat.format(docetConf.getPathToFaq(), lang) + "/" + faqId + EXTENSION_HTML;
    }

    private String getPagePathByIdForPackageAndLanguage(final String packageName, final String pageId, final String lang,
        final DocetExecutionContext ctx)
        throws DocetPackageException, IOException {
        final String basePath = this.getPathToPackageDoc(packageName, ctx);

        final String pathToPage;
        if (this.docetConf.isPreviewMode()) {
            final String docetDocsBasePath = basePath + "/" + MessageFormat.format(docetConf.getPathToPages(), lang);
            final Path pagePath = searchFileInBasePathByName(Paths.get(docetDocsBasePath), pageId + EXTENSION_HTML);
            if (pagePath == null) {
                throw new IOException("Page " + pageId + " for language " + lang + " not found!");
            } else {
                pathToPage = searchFileInBasePathByName(Paths.get(docetDocsBasePath), pageId + EXTENSION_HTML).toString();
            }
        } else {
            pathToPage = basePath + "/" + MessageFormat.format(docetConf.getPathToPages(), lang) + "/" + pageId + EXTENSION_HTML;
        }
        return pathToPage;
    }

    private static Path searchFileInBasePathByName(final Path basePath, final String fileName) throws IOException {
        final Holder<Path> result = new Holder<>();
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

    private DocetDocument loadPdfSummaryForPackage(final String packageName, final String docId, final String lang,
        final DocetExecutionContext ctx)
        throws DocetPackageException, IOException {
        final String basePath = this.getPathToPackageDoc(packageName, ctx);
        return DocetDocument.parseTocToDocetDocument(new String(
                DocetUtils.fastReadFile(
                    new File(basePath + MessageFormat.format(this.docetConf.getPathToPdfSummaries(), lang, docId))
                            .toPath()),
                ENCODING_UTF_8), packageName, lang);
    }

    private Document loadTocForPackage(final String packageName, final String lang, final DocetExecutionContext ctx)
        throws DocetPackageException, IOException {
        final String basePath = this.getPathToPackageDoc(packageName, ctx);
        return Jsoup
            .parseBodyFragment(new String(
                DocetUtils.fastReadFile(
                    new File(basePath + MessageFormat.format(this.docetConf.getTocFilePath(), lang)).toPath()),
                ENCODING_UTF_8), ENCODING_UTF_8);
    }

    private Document parseTocForPackage(final String packageName, final String lang,
        final Map<String, String[]> params, final DocetExecutionContext ctx)
        throws DocetPackageException, IOException {
        final Document docToc = loadTocForPackage(packageName, lang, ctx);

        // inject default docet menu css class on main menu
        docToc.select(DOM_PATH_TO_TOC_LIST).addClass(CSS_CLASS_DOCET_MENU);
        docToc.select(DOM_PATH_TO_TOC_LIST).attr(DOCET_ATTR_PACKAGE, packageName);
        docToc.select(DOM_PATH_TO_TOC_LIST).addClass(CSS_CLASS_DOCET_MENU_VISIBLE);
        docToc.select(DOM_PATH_TO_TOC_LIST +" > li").addClass(CSS_CLASS_DOCET_MENU);

        if (this.docetConf.isFaqTocAtRuntime()) {
            injectFaqItemsInTOC(docToc);
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
        final Map<String, String[]> additionalParams, final Document toc, final DocetPage doc) {
        final int docType = doc.getType();
        final String pageLink;
        final String pageId;
        final String[] breadCrumbs;
        switch (docType) {
            case DocetPage.DOCTYPE_FAQ:
                pageLink = MessageFormat.format(this.docetConf.getLinkToFaqPattern(), packageId, doc.getId(), lang);
                pageId = "faq_" + doc.getId() + "_" + lang;
                breadCrumbs = new String[]{getFaqPath()};
                break;
            case DocetPage.DOCTYPE_PAGE:
                pageLink = MessageFormat.format(this.docetConf.getLinkToPagePattern(), packageId, doc.getId(), lang);
                pageId = doc.getId() + "_" + lang;
                breadCrumbs = createBreadcrumbsForPageFromToc(packageId, pageId, toc);
                break;
            default:
                throw new IllegalArgumentException("Unsupported document type " + docType);
        }
        return SearchResult.toSearchResult(packageId, doc, pageId, appendParamsToUrl(pageLink, additionalParams), breadCrumbs);
    }

    private void injectFaqItemsInTOC(final Document toc) throws IOException {
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

        final Holder<Integer> countFaqs = new Holder<>();
        countFaqs.setValue(0);
        faqList.select("a").forEach(faqA -> {
            faqA.addClass(CSS_CLASS_DOCET_FAQ_LINK);
            countFaqs.setValue(countFaqs.getValue() + 1);
        });

        if (countFaqs.getValue() > 0) {
            toc.select(DOM_PATH_TO_TOC_LIST + " > li").addClass(CSS_CLASS_DOCET_MENU_HASSUBMENU);
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

    private String parsePackageIconUrl(final String packageName, final Map<String, String[]> params) {
        String url = MessageFormat.format(this.docetConf.getLinkToPackageIconPattern(), packageName);
        url = appendParamsToUrl(url, params);
        return url;
    }

    private void parseImage(final String packageName, final Element item, final String lang, final DocetDocFormat format,
        final Map<String, String[]> params, final DocetExecutionContext ctx) throws DocetPackageException, IOException {
        final String[] imgPathTokens = item.attr("src").split("/");
        final String imgName = imgPathTokens[imgPathTokens.length - 1];
        if (format.isIncludeResources()) {
            final String basePathToPackage = this.getPathToPackageDoc(packageName, ctx);
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
            item.attr("src", "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(Files.readAllBytes(new File(pathToImg).toPath())));
        } else {
            final String imgNameNormalizedExtension = imgName + IMAGE_DOCET_EXTENSION;
            String href = MessageFormat.format(this.docetConf.getLinkToImagePattern(), packageName, lang, imgNameNormalizedExtension);
            href = appendParamsToUrl(href, params);
            item.attr("src", href);
        }
    }

    private void parseTOCItem(final String packageName, final Element item, String lang, final Map<String, String[]> params) {
        final String[] pageNameTokens = item.attr("href").split(EXTENSION_HTML);
        final String barePagename = pageNameTokens[0];
        final String fragment;
        if (pageNameTokens.length == 2) {
            fragment = pageNameTokens[1];
        } else {
            fragment = "";
        }
        // check if the linked document is written in another language!
        final String referenceLanguage = item.attr(DOCET_HTML_ATTR_REFERENCE_LANGUAGE_NAME);
        final String parsedLanguage;
        if (!referenceLanguage.isEmpty()) {
            parsedLanguage = referenceLanguage;
        } else {
            parsedLanguage = lang;
        }

        String href;
        if (item.hasClass(CSS_CLASS_DOCET_FAQ_LINK)) {
            href = MessageFormat.format(this.docetConf.getLinkToFaqPattern(), packageName, barePagename, parsedLanguage)
                + fragment;
            // determine page id: if page name is samplepage_it.html
            // then id will be simply samplepage_it
            if (!item.attr("id").equals(ID_DOCET_FAQ_MAIN_LINK)) {
                item.attr("id", "faq_" + barePagename + "_" + parsedLanguage);
            }
        } else {
            href = MessageFormat.format(this.docetConf.getLinkToPagePattern(), packageName, barePagename, parsedLanguage)
                + fragment;
            // determine page id: if page name is samplepage_it.html
            // then id will be simply samplepage_it
            final String linkId = barePagename + "_" + parsedLanguage
                + (fragment.isEmpty() ? "" : fragment.replaceAll("#", "_"));
            item.attr("id", linkId);
            item.attr("title", item.text());
        }
        href = appendParamsToUrl(href, params);
        item.addClass(CSS_CLASS_DOCET_MENU_LINK);
        item.attr(DOCET_ATTR_DOCETREF, href);
        item.removeAttr("href");
        item.attr(DOCET_ATTR_PACKAGE, packageName);
    }

    private void parseAnchorItemInPage(final String packageName, final Element item, final String lang, final Map<String, String[]> params) {
        final String crossPackageId = item.attr(DOCET_ATTR_PACKAGE);
        final String anchorHref = item.attr("href");
        final String extension;
        if (anchorHref.endsWith(".pdf")) {
            extension = EXTENSION_PDF;
        } else {
            extension = EXTENSION_HTML;
        }
        final String[] pageNameTokens = item.attr("href").split(extension);
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
            if (EXTENSION_PDF.equals(extension)) {
                href = MessageFormat.format(this.docetConf.getLinkToPdfPattern(), ultimatePackageId, barePagename, lang) + fragment;
            } else {
                href = MessageFormat.format(this.docetConf.getLinkToPagePattern(), ultimatePackageId, barePagename, lang) + fragment;
            }
            linkId = barePagename + "_" + lang;
        }
        href = appendParamsToUrl(href, params);
        // determine page id: if page name is samplepage_it.html
        // then id will be simply samplepage_it
        if (item.attr("href").startsWith("#")) {
            item.attr(DOCET_ATTR_DOCETREF, item.attr("href"));
        } else if (!item.attr("href").isEmpty()) {
            item.attr("id", linkId);
            item.attr(DOCET_ATTR_DOCETREF, href);
            item.attr(DOCET_ATTR_PACKAGE, ultimatePackageId);
        }
        item.removeAttr("href");
        if (EXTENSION_PDF.equals(extension)) {
            item.addClass(CSS_CLASS_DOCET_PDF_LINK);
            
        } else {
            item.addClass(CSS_CLASS_DOCET_PAGE_LINK);
        }
    }

    private String appendParamsToUrl(final String url, final Map<String, String[]> params) {
        final String parsedUrl;
        if (params == null || params.isEmpty()) {
            parsedUrl = url;
        } else {

            final Holder<String> tmpUrl = new Holder<>();
            tmpUrl.setValue(url + "?");
            params.entrySet().stream().filter(entry -> !"id".equals(entry.getKey()) && !"lang".equals(entry.getKey()))
                .forEach(entry -> {
                    try {
                        tmpUrl.setValue(tmpUrl.getValue() + entry.getKey()
                            + "=" + URLEncoder.encode(entry.getValue()[0], ENCODING_UTF_8) + "&");
                    } catch (UnsupportedEncodingException impossibile) {
                        LOGGER.log(Level.SEVERE, "impossible to encode param {0}", impossibile);
                    }
                });
            final String tmpUrlValue = tmpUrl.getValue();
            if (tmpUrlValue.endsWith("?")) {
                parsedUrl = tmpUrlValue.substring(0, tmpUrlValue.lastIndexOf('?'));
            } else {
                parsedUrl = tmpUrlValue.substring(0, tmpUrlValue.lastIndexOf('&'));
            }
        }
        return parsedUrl;
    }

    private String getLinkToPackageMainPage(final String packageId, final String lang, final Map<String, String[]> additionalParams) {
        return this.appendParamsToUrl(
            MessageFormat.format(this.docetConf.getLinkToPagePattern(), packageId, "main", lang),
            additionalParams);
    }

    private PackageResponse servePackageDescriptionForLanguage(final String[] packagesId, final String lang,
        final Map<String, String[]> additionalParams, final DocetExecutionContext ctx, final HttpServletRequest request) {
        PackageResponse packageResponse;
        final List<PackageDescriptionResult> results = new ArrayList<>();
        final String[] requestedPackages;
        if (packagesId == null) {
            requestedPackages = new String[]{};
        } else {
            requestedPackages = packagesId;
        }
        for (final String packageId : requestedPackages) {
            try {
                final String pathToPackage = this.getPathToPackageDoc(packageId, ctx);
                final Document descriptor = Jsoup.parseBodyFragment(
                    new String(
                        DocetUtils.fastReadFile(new File(pathToPackage).toPath().resolve("descriptor.html")), ENCODING_UTF_8));
                final Elements divDescriptor = descriptor.select("div[lang=" + lang + "]");
                final String title;
                final String desc;
                final String imageIcoPath;
                final boolean packageFound;
                String actualLanguage = lang;
                if (divDescriptor.isEmpty()) {
                    LOGGER.log(Level.WARNING, "Descriptor for package '"
                        + packageId + "' is empty for language '" + lang + "'. Skipping...");
                    title = "";
                    desc = "";
                    packageFound = false;
                } else {
                    title = divDescriptor.select("h1").get(0).text();
                    desc = divDescriptor.select("p").get(0).text();
                    if (divDescriptor.hasAttr(DOCET_HTML_ATTR_REFERENCE_LANGUAGE_NAME)) {
                        actualLanguage = divDescriptor.attr(DOCET_HTML_ATTR_REFERENCE_LANGUAGE_NAME);
                    }
                    packageFound = true;
                }
                if (packageFound) {
                    final String packageLink = getLinkToPackageMainPage(packageId, actualLanguage, additionalParams);
                    imageIcoPath = this.buildPackageIconPath(packageId, pathToPackage, additionalParams, request);
                    final PackageDescriptionResult res
                        = new PackageDescriptionResult(title, packageId, packageLink, desc, imageIcoPath, actualLanguage, null);
                    results.add(res);
                }
            } catch (IOException | DocetPackageException ex) {
                LOGGER.log(Level.SEVERE,
                    "Descriptor for package '" + packageId + "' not found for language '" + lang + "'", ex);
                final PackageDescriptionResult res
                    = new PackageDescriptionResult(null, packageId, null, null, null, lang, "package_not_found");
                results.add(res);
            }
        }
        packageResponse = new PackageResponse();
        packageResponse.addItems(results);
        return packageResponse;
    }

    private String buildPackageIconPath(final String packageId, final String pathToPackage,
        final Map<String, String[]> additionalParams, final HttpServletRequest request) {
        final String iconPath;
        final File iconFile = new File(pathToPackage).toPath().resolve("icon.png").toFile();
        if (iconFile.exists()) {
            iconPath = parsePackageIconUrl(packageId, additionalParams);
        } else {
            iconPath = request.getContextPath() + "/docetres/docet/doc-default.png";
        }
        return iconPath;
    }

    private SearchResponse searchPagesByKeywordAndLangWithRerencePackage(final String searchText, final String lang,
        final String sourcePackageName, final Set<String> enabledPackages, final Map<String, String[]> additionalParams,
        final DocetExecutionContext ctx)
        throws DocetException {
        SearchResponse searchResponse;

        final List<PackageSearchResult> results = new ArrayList<>();
        final Holder<PackageSearchResult> packageResForCurrentPackage = new Holder<>();
        final Map<String, List<SearchResult>> docsForPackage = new HashMap<>();
        final Map<String, String> errorForPackage = new HashMap<>();
        final String[] exactSearchTokens = DocetUtils.parsePageIdSearchToTokens(searchText);
        //choose search type: search for a specific doc rather than do an extensive search on a set of given packages
        if (exactSearchTokens.length == 2) {
            final String packageid = exactSearchTokens[0];
            final String pageid = exactSearchTokens[1];
            try {
                final DocetDocumentSearcher packageSearcher
                    = this.packageRuntimeManager.getSearchIndexForPackage(packageid, ctx);
                final DocetPage foundDoc
                    = packageSearcher.searchDocumentById(pageid, lang);
                final List<SearchResult> packageSearchRes = new ArrayList<>();
                if (foundDoc != null) {
                    final Document toc
                        = parseTocForPackage(packageid, lang, additionalParams, ctx);
                    packageSearchRes
                        .add(this.convertDocetDocumentToSearchResult(lang, packageid, additionalParams, toc, foundDoc));
                }
                docsForPackage.put(packageid, packageSearchRes);
            } catch (DocetPackageException | DocetDocumentSearchException | IOException ex) {
                LOGGER.log(Level.WARNING, "Error on completing search '" + searchText + "' on package '"
                    + packageid + "'", ex);
                errorForPackage.put(packageid, ex.getMessage());
            }
        } else {
            for (final String packageId : enabledPackages) {
                final List<DocetPage> docs = new ArrayList<>();
                final List<SearchResult> packageSearchRes = new ArrayList<>();
                try {
                    final DocetDocumentSearcher packageSearcher
                        = this.packageRuntimeManager.getSearchIndexForPackage(packageId, ctx);
                    docs.addAll(
                        packageSearcher
                            .searchForMatchingDocuments(searchText, lang, this.docetConf.getMaxSearchResultsForPackage()));
                    final Document toc = parseTocForPackage(packageId, lang, additionalParams, ctx);
                    docs.stream().sorted((d1, d2) -> d2.getRelevance() - d1.getRelevance()).forEach(e -> {
                        final SearchResult searchRes
                            = this.convertDocetDocumentToSearchResult(lang, packageId, additionalParams, toc, e);
                        packageSearchRes.add(searchRes);
                    });
                    docsForPackage.put(packageId, packageSearchRes);
                } catch (IOException | DocetDocumentSearchException | DocetPackageException ex) {
                    LOGGER.log(Level.WARNING, "Error on completing search '"
                        + searchText + "' on package '" + packageId + "'", ex);
                    errorForPackage.put(packageId, ex.getMessage());
                }
            }
        }
        docsForPackage.entrySet().stream().forEach(entry -> {
            final String packageid = entry.getKey();
            final List<SearchResult> searchRes = entry.getValue();
            String packageName = null;
            final String packageLink = getLinkToPackageMainPage(packageid, lang, additionalParams);
            try {
                final DocetPackageDescriptor desc = this.packageRuntimeManager.getDescriptorForPackage(packageid, ctx);
                packageName = desc.getLabelForLang(lang);
            } catch (DocetPackageException ex) {
                LOGGER.log(Level.WARNING, "Package name not found in descriptor for package " + packageid, ex);
            }
            final PackageSearchResult packageRes = PackageSearchResult.toPackageSearchResult(packageid, packageName,
                packageLink, searchRes, errorForPackage.get(packageid));
            if (packageid.equals(sourcePackageName)) {
                packageResForCurrentPackage.setValue(packageRes);
            } else {
                results.add(packageRes);
            }
        });
        errorForPackage.entrySet().stream().forEach(entry -> {
            final String packageid = entry.getKey();
            final String errorMsg = entry.getValue();
            String packageName = null;
            final String packageLink = getLinkToPackageMainPage(packageid, lang, additionalParams);
            try {
                final DocetPackageDescriptor desc = this.packageRuntimeManager.getDescriptorForPackage(packageid, ctx);
                packageName = desc.getLabelForLang(lang);
            } catch (DocetPackageException ex) {
                LOGGER.log(Level.WARNING, "Package name not found in descriptor for package " + packageid, ex);
            }
            final PackageSearchResult packageRes = PackageSearchResult.toPackageSearchResult(packageid, packageName,
                packageLink, new ArrayList<>(), errorMsg);
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
        return searchResponse;
    }

    private String[] createBreadcrumbsForPageFromToc(final String packageId, final String pageId, final Document toc) {
        List<String> crumbs = new ArrayList<>();
        Elements pageLinks = toc.getElementsByTag("a");
        Optional<Element> pageLink = pageLinks.stream().filter(link -> link.attr("id").trim().equals(pageId)).findFirst();
        if (pageLink.isPresent()) {
            final Element tocLink = pageLink.get();
            Element parentUl = tocLink.parent().parent().parent();
            Element parent = parentUl.parent();
            while (parent != null && "li".equalsIgnoreCase(parent.tagName())) {
                Element anchorToAdd = parent.getElementsByTag("div").get(0).getElementsByTag("a").get(0);
                anchorToAdd.attr(DOCET_ATTR_PACKAGE, packageId);
                crumbs.add(anchorToAdd.outerHtml());
                // possibly a ul
                Element ul = parent.parent();
                if (ul == null) {
                    parent = null;
                } else {
                    parent = ul.parent();
                }
            }
        }
        Collections.reverse(crumbs);
        return crumbs.toArray(new String[]{});
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

    /**
     * Main integration method.
     *
     * @param request
     * @param response
     * @throws DocetException
     */
    public void serveRequest(HttpServletRequest request, HttpServletResponse response) throws DocetException {
        this.serveRequest(request, response, null);
    }

    /**
     * Main integration method.
     *
     * @param request
     * @param response
     * @param statsCollector allows details about request to be collected
     * @throws DocetException
     */
    public void serveRequest(HttpServletRequest request, HttpServletResponse response, StatsCollector statsCollector)
        throws DocetException {
        String base = request.getContextPath() + request.getServletPath();
        final String reqPath = request.getRequestURI().substring(base.length());
        if (reqPath.matches(URL_PATTERN)) {
            final DocetExecutionContext ctx = new DocetExecutionContext(request);
            final Map<String, String[]> additionalParams = request.getParameterMap();

            String[] tokens = reqPath.substring(1).split("/");
            String lang = Optional.ofNullable(request.getParameter("lang")).orElse(this.docetConf.getDefaultLanguage());
            final String packageId;
            if (tokens.length > 1) {
                packageId = tokens[1];
            } else {
                packageId = null;
            }

            final DocetRequestType req = DocetRequestType.parseDocetRequestByName(tokens[0]);
            switch (req) {
                case TYPE_TOC:
                    final String packageIdParam = request.getParameter("packageId");
                    this.serveTableOfContentsRequest(packageIdParam, lang, additionalParams, ctx, response);
                    break;
                case TYPE_FAQ:
                case TYPE_MAIN:
                case TYPE_PAGES:
                    DocetDocFormat format = DocetDocFormat.TYPE_HTML;
                    String[] pageFields = tokens[2].split("_");
                    final String pageName = pageFields[1];
                    if (pageName.endsWith(".mndoc")) {
                        lang = pageName.split(".mndoc")[0];
                    } else if (pageName.endsWith(".pdf")) {
                        lang = pageName.split(".pdf")[0];
                        format = DocetDocFormat.TYPE_PDF;
                    }
                    final String pageId = pageFields[0];
                    this.servePageRequest(packageId, pageId, lang, req == DocetRequestType.TYPE_FAQ, format, additionalParams,
                        ctx, response);
                    if (statsCollector != null) {
                        final Map<String, Object> details = new HashMap<>();
                        details.put(STATS_DETAILS_PACKAGE_ID, packageId);
                        details.put(STATS_DETAILS_PAGE_ID, pageId);
                        details.put(STATS_DETAILS_LANGUAGE, lang);
                        statsCollector.afterRequest(req, details);
                    }
                    break;
                case TYPE_PDFS:
                    final String[] reqFields = tokens[2].split("_");
                    final String pdfname = reqFields[1];
                    lang = pdfname.split(".pdf")[0]; 
                    final String documentId = reqFields[0];
                    try (final OutputStream out = response.getOutputStream();) {
                        final DocetDocument doc = this.loadPdfSummaryForPackage(packageId, documentId, lang, ctx);
                        this.documentGenerator.generateDocetDocument(doc, ctx, out);
                        if (statsCollector != null) {
                            final Map<String, Object> details = new HashMap<>();
                            details.put(STATS_DETAILS_PACKAGE_ID, packageId);
                            details.put(STATS_DETAILS_PAGE_ID, documentId + ".pdf");
                            details.put(STATS_DETAILS_LANGUAGE, lang);
                            statsCollector.afterRequest(req, details);
                        }
                    } catch (DocetPackageException | IOException | DocetDocumentParsingException ex) {
                       LOGGER.log(Level.SEVERE, "Error on serving pdf " + pdfname + " for package " + packageId, ex);
                       new DocetException(DocetException.CODE_GENERIC_ERROR, "Impossible to generate pdf", ex);
                    }
                    break;
                case TYPE_ICONS:
                    this.serveIconRequest(packageId, ctx, response);
                    break;
                case TYPE_IMAGES:
                    String[] imgFields = tokens[2].split("_");
                    lang = imgFields[0];
                    final String imgName = imgFields[1].split(".mnimg")[0];
                    this.serveImageRequest(packageId, imgName, lang, ctx, response);
                    break;
                case TYPE_SEARCH:
                    final String sourcePackage = request.getParameter("sourcePkg");
                    final String[] packages = request.getParameterValues("enablePkg[]");
                    final String query = request.getParameter("q");
                    this.serveSearchRequest(query, lang, packages, sourcePackage, additionalParams, ctx, response);
                    if (statsCollector != null) {
                        final Map<String, Object> details = new HashMap<>();
                        details.put(STATS_DETAILS_SEARCH_TERM, query);
                        if (sourcePackage != null) {
                            details.put(STATS_DETAILS_SEARCH_SOURCE_PACKAGE, sourcePackage);
                        }
                        details.put(STATS_DETAILS_LANGUAGE, lang);
                        statsCollector.afterRequest(req, details);
                    }
                    break;
                case TYPE_PACKAGE:
                    String[] packageIds = request.getParameterValues("id");
                    this.servePackageListRequest(lang, packageIds, additionalParams, ctx, request, response);
                    break;
                default:
                    LOGGER.log(Level.SEVERE, "Request {0} for package {1} language {2} path {3} is not supported",
                        new Object[]{req, packageId, lang, reqPath});
                    throw new DocetException(DocetException.CODE_GENERIC_ERROR, "Unsupported request, path " + reqPath);
            }
        } else {
            LOGGER.log(Level.SEVERE, "Impossibile to find a matching service for request {0}", new Object[]{reqPath});
            throw new DocetException(DocetException.CODE_GENERIC_ERROR, "Impossible to serve request " + reqPath);
        }
    }

    public boolean existsPageForLanguage(final String packageId, final String pageId, final String lang,
        final DocetExecutionContext ctx) throws DocetPackageException, IOException {
        Document toc = loadTocForPackage(packageId, lang, ctx);
        return this.pageIdPresentinTOC(pageId, toc);
        
    }

    private boolean pageIdPresentinTOC(final String pageId, final Document toc) {
        return toc.getElementsByTag("a").stream().map(a -> a.attr("href").split(EXTENSION_HTML)[0])
                        .filter(id -> pageId.equals(id)).findFirst().isPresent();
    }

    private void serveTableOfContentsRequest(final String packageId, final String lang,
        final Map<String, String[]> params, final DocetExecutionContext ctx, final HttpServletResponse response)
        throws DocetException {
        response.setCharacterEncoding(ENCODING_UTF_8);
        response.setContentType("text/html; charset=" + ENCODING_UTF_8);
        try (PrintWriter out = response.getWriter();) {
            final String html = this.serveTableOfContentsForPackage(packageId, lang, params, ctx);
            out.write(html);
        } catch (DocetException ex) {
            LOGGER.log(Level.SEVERE, "Error on serving TOC packageid " + packageId + " lang ", ex);
            throw ex;
        } catch (IOException ex) {
            throw new DocetException(DocetException.CODE_GENERIC_ERROR, "Error on sending response", ex);
        }
    }

    private void servePageRequest(final String packageId, final String pageId, final String lang, final boolean isFaq,
        final DocetDocFormat format, final Map<String, String[]> params, final DocetExecutionContext ctx, final HttpServletResponse response)
        throws DocetException {
            try (OutputStream out = response.getOutputStream();) {
                final String html = this.servePageIdForLanguageForPackage(packageId, pageId, lang, format, isFaq, params, ctx);
                switch (format) {
                    case TYPE_PDF:
                        out.write(this.parserFactory.getParserForFormat(format).parsePage(html));
                        break;
                    case TYPE_HTML:
                    default:
                        response.setCharacterEncoding(ENCODING_UTF_8);
                        response.setContentType("text/html; charset=" + ENCODING_UTF_8);
                        out.write(html.getBytes(ENCODING_UTF_8));
                }
            } catch (DocetException ex) {
                LOGGER.log(Level.SEVERE, "Error on serving Page " + pageId + " packageid " + packageId + " lang ", ex);
                throw ex;
            } catch (DocetDocumentParsingException ex) {
                throw new DocetException(DocetException.CODE_GENERIC_ERROR, "Error on sending response", ex);
            } catch (IOException ex) {
                throw new DocetException(DocetException.CODE_GENERIC_ERROR, "Error on sending response", ex);
            }
    }

//    private void servePdfRequest(final String packageId, final String pdfId, final String lang, 
//        final Map<String, String[]> params, final DocetExecutionContext ctx, final HttpServletResponse response)
//        throws DocetException {
//            try {
//                final String html = this.servePageIdForLanguageForPackage(packageId, pageId, lang, format, isFaq, params, ctx);
//                this.parserFactory.getParserForFormat(format).parsePage(html, response);
//            } catch (DocetException ex) {
//                LOGGER.log(Level.SEVERE, "Error on serving Page " + pageId + " packageid " + packageId + " lang ", ex);
//                throw ex;
//            } catch (DocetDocumentParsingException ex) {
//                throw new DocetException(DocetException.CODE_GENERIC_ERROR, "Error on sending response", ex);
//            }
//    }

    private void serveSearchRequest(final String query, final String lang,
        final String[] packages, String sourcePackage, final Map<String, String[]> params,
        final DocetExecutionContext ctx, final HttpServletResponse response)
        throws DocetException {
        final Map<String, String[]> additionalParams = new HashMap<>();
        params.entrySet()
            .stream()
            .filter(entry -> !"q".equals(entry.getKey()) && !"lang".equals(entry.getKey())
                                        && !"sourcePkg".equals(entry.getKey()) && !"enablePkg".equals(entry.getKey()))
            .forEach(e -> {
                additionalParams.put(e.getKey(), e.getValue());
            });

        final Set<String> inScopePackages = new HashSet<>();
        if (packages != null && packages.length > 0) {
            inScopePackages.addAll(Arrays.asList(packages));
        }
        final String srcPackageParsed;
        if (sourcePackage == null) {
            srcPackageParsed = "";
        } else {
            srcPackageParsed = sourcePackage;
            inScopePackages.add(sourcePackage);
        }
        try (OutputStream out = response.getOutputStream();) {
            final SearchResponse searchResp = this.searchPagesByKeywordAndLangWithRerencePackage(query, lang,
                srcPackageParsed, inScopePackages, additionalParams, ctx);
            String json = new ObjectMapper().writeValueAsString(searchResp);
            response.setContentType("application/json;charset=" + ENCODING_UTF_8);
            out.write(json.getBytes(ENCODING_UTF_8));
        } catch (DocetException ex) {
            LOGGER.log(Level.SEVERE, "Error on serving search query " + query + " lang ", ex);
            throw ex;
        } catch (IOException ex) {
            throw new DocetException(DocetException.CODE_GENERIC_ERROR, "Error on sending response", ex);
        }
    }

    private void servePackageListRequest(final String lang, final String[] packageIds, final Map<String, String[]> params,
        final DocetExecutionContext ctx, final HttpServletRequest request, final HttpServletResponse response)
        throws DocetException {
        final Map<String, String[]> additionalParams = new HashMap<>();
        params.entrySet()
            .stream()
            .filter(entry -> !"q".equals(entry.getKey()) && !"lang".equals(entry.getKey())
                                        && !"sourcePkg".equals(entry.getKey()) && !"enablePkg".equals(entry.getKey()))
            .forEach(e -> {
                additionalParams.put(e.getKey(), e.getValue());
            });

        try (OutputStream out = response.getOutputStream();) {

            final PackageResponse packageResp
                = this.servePackageDescriptionForLanguage(packageIds, lang, additionalParams, ctx, request);
            String json = new ObjectMapper().writeValueAsString(packageResp);
            response.setContentType("application/json;charset=" + ENCODING_UTF_8);
            out.write(json.getBytes(ENCODING_UTF_8));
        } catch (IOException ex) {
            throw new DocetException(DocetException.CODE_GENERIC_ERROR, "Error on sending response", ex);
        }
    }

    private void serveImageRequest(final String packageId, final String imageName, final String lang,
        final DocetExecutionContext ctx, final HttpServletResponse response)
        throws DocetException {
        final String imageFormat = imageName.substring(imageName.indexOf('.') + 1);
        if (!"png".equals(imageFormat)) {
            throw new DocetException(DocetException.CODE_RESOURCE_NOTFOUND,
                "Unsupported image file format " + imageFormat);
        }
        response.setContentType("image/png");
        try (OutputStream out = response.getOutputStream();) {
            this.getImageBylangForPackage(imageName, lang, packageId, out, ctx);
        } catch (DocetException ex) {
            LOGGER.log(Level.SEVERE, "Error on serving Image " + imageName + " packageid " + packageId + " lang ", ex);
            throw ex;
        } catch (IOException ex) {
            throw new DocetException(DocetException.CODE_GENERIC_ERROR, "Error on sending response", ex);
        }
    }

    private void serveIconRequest(final String packageId, final DocetExecutionContext ctx,
        final HttpServletResponse response)
        throws DocetException {

        response.setContentType("image/png");
        try (OutputStream out = response.getOutputStream();) {
            this.getIconForPackage(packageId, out, ctx);
        } catch (DocetException ex) {
            LOGGER.log(Level.SEVERE, "Error on serving Icon for package " + packageId, ex);
            throw ex;
        } catch (IOException ex) {
            throw new DocetException(DocetException.CODE_GENERIC_ERROR, "Error on sending response", ex);
        }
    }
    
    private class DocetDocumentParserFactory {
        private Map<DocetDocFormat, DocetDocumentParser> parsersForFormat;

        private DocetDocumentParserFactory() {
            this.parsersForFormat = new EnumMap<>(DocetDocFormat.class);
        }

        private DocetDocumentParser getParserForFormat(final DocetDocFormat format) throws DocetException {
            DocetDocumentParser parser = this.parsersForFormat.get(format);
            if (parser != null) {
                return parser;
            }
            switch (format) {
                case TYPE_PDF:
                    try {
                        final String customCssPath = docetConf.getPathToCustomCss();
                        if (customCssPath.isEmpty()) {
                            parser = new PdfDocetDocumentParser(new String(DocetUtils.readStream(getClass().getClassLoader().getResourceAsStream("docetdoc.css")), ENCODING_UTF_8));
                        } else {
                            parser = new PdfDocetDocumentParser(new String(DocetUtils.fastReadFile(new File(customCssPath).toPath()), ENCODING_UTF_8));
                        }
                        
                    } catch (Exception e) {
                        throw new DocetException(DocetException.CODE_GENERIC_ERROR, "Impossible to retrieve pdf Parser", e);
                    }
                    break;
                case TYPE_HTML:
                default:
                    parser = new HtmlDocetDocumentParser();
            }
            this.parsersForFormat.put(format, parser);
            return parser;
        }
    }
}
