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

import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.Shape;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.parser.Parser;
import org.jsoup.parser.Tag;
import org.jsoup.select.Elements;
import org.xhtmlrenderer.context.StyleReference;
import org.xhtmlrenderer.css.style.CalculatedStyle;
import org.xhtmlrenderer.extend.NamespaceHandler;
import org.xhtmlrenderer.extend.UserInterface;
import org.xhtmlrenderer.layout.BoxBuilder;
import org.xhtmlrenderer.layout.Layer;
import org.xhtmlrenderer.layout.LayoutContext;
import org.xhtmlrenderer.layout.SharedContext;
import org.xhtmlrenderer.pdf.ITextFontContext;
import org.xhtmlrenderer.pdf.ITextFontResolver;
import org.xhtmlrenderer.pdf.ITextOutputDevice;
import org.xhtmlrenderer.pdf.ITextReplacedElementFactory;
import org.xhtmlrenderer.pdf.ITextTextRenderer;
import org.xhtmlrenderer.pdf.ITextUserAgent;
import org.xhtmlrenderer.render.BlockBox;
import org.xhtmlrenderer.render.PageBox;
import org.xhtmlrenderer.render.RenderingContext;
import org.xhtmlrenderer.render.ViewportBox;
import org.xhtmlrenderer.simple.extend.XhtmlNamespaceHandler;
import org.xhtmlrenderer.util.Configuration;

import com.lowagie.text.DocumentException;
import com.lowagie.text.pdf.PdfDestination;
import com.lowagie.text.pdf.PdfOutline;
import com.lowagie.text.pdf.PdfWriter;

import docet.DocetDocumentPlaceholder;
import docet.DocetDocumentResourcesAccessor;
import docet.DocetExecutionContext;
import docet.DocetLanguage;
import docet.SimpleDocetDocumentAccessor;
import docet.error.DocetDocumentParsingException;
import docet.error.DocetException;
import docet.model.DocetDocument;
import docet.model.SummaryEntry;

/**
 * Generates a PDF from a {@link DocetDocument} using <tt>xhtmlrenderer</tt>
 *
 * @author diego.salvi
 */
public class PDFDocumentHandler {

    private static final Logger LOGGER = Logger.getLogger(PDFDocumentHandler.class.getName());

    private static final DocetDocumentResourcesAccessor DEFAULT_ACCESSOR = new SimpleDocetDocumentAccessor();

    public static final DocetLanguage DEFAULT_LANGUAGE = DocetLanguage.EN;

    /* These two defaults combine to produce an effective resolution of 96 px to the inch */
    public static final float DEFAULT_DOTS_PER_POINT = 20f * 4f / 3f;
    public static final int DEFAULT_DOTS_PER_PIXEL = 20;

    public static final String DEFAULT_BASE_URL = "";

    public static final String DEFAULT_CSS = "docetpdf.css";
    public static final String DEFAULT_COVER_IMAGE = "pdfcover.png";
    public static final String DEFAULT_FOOTER_COVER = "Docet - &copy;&nbsp;Copyright 2017";

    private static final W3CDom W3CDOM = new W3CDom();

    public static final Parser HTML_PARSER = Parser.htmlParser();

    /** Patterns for replace placeholders */
    private static final Map<HTMLPlaceholder,Pattern> REPLACEMENT_PATTERNS;
    static {
        REPLACEMENT_PATTERNS = new EnumMap<>(HTMLPlaceholder.class);

        for(HTMLPlaceholder placeholder : HTMLPlaceholder.values()) {
            REPLACEMENT_PATTERNS.put(placeholder, Pattern.compile("${" + placeholder + "}", Pattern.LITERAL));
        }
    }


    private final float dotsPerPoint;
    private final int dotsPerPixel;

    private final String baseURL;
    private final NamespaceHandler namespaceHandler;

    private String css;

    private final DocetDocument document;

    private final DocetManager manager;
    private final DocetExecutionContext context;
    private final DocetDocumentResourcesAccessor accessor;
    private final DocetLanguage language;

    private final List<DocumentPart> documents = new ArrayList<>();

    private final SharedContext sharedContext;
    private final ITextOutputDevice outputDevice;

    private final Document cover;
    private final Element pageHead;
    private final Element pageHeadBody;

    private final Map<HTMLPlaceholder,String> placeholders;

    public static interface Builder {

        public Builder manager(DocetManager manager);

        public Builder context(DocetExecutionContext context);

        public Builder placeholders(DocetDocumentResourcesAccessor accessor);

        public Builder language(DocetLanguage language);

        public Builder dotsPerPoint(float dotsPerPoint);

        public Builder dotsPerPixel(int dotsPerPixel);

        public Builder baseURL(String url);

        public Builder namespaceHandler(NamespaceHandler namespaceHandler);

        public PDFDocumentHandler create(DocetDocument document) throws DocetDocumentParsingException;

    }

    public static final Builder builder() {
        return new BuilderImpl();
    }

    private PDFDocumentHandler(
            float dotsPerPoint,
            int dotsPerPixel,
            String baseURL,
            NamespaceHandler namespaceHandler,
            DocetDocument document,
            DocetManager manager,
            DocetExecutionContext context,
            DocetDocumentResourcesAccessor accessor,
            DocetLanguage language)
                    throws DocetDocumentParsingException {

        this.dotsPerPoint = dotsPerPoint;
        this.dotsPerPixel = dotsPerPixel;

        this.baseURL = baseURL;
        this.namespaceHandler = namespaceHandler;

        this.document = document;

        this.manager = manager;
        this.context = context;
        this.accessor = accessor;
        this.language = language;

        this.css = evaluateAccessorConfiguration(DocetDocumentPlaceholder.PDF_CSS, DEFAULT_CSS);

        /* ************************* */
        /* *** DATA PLACEHOLDERS *** */
        /* ************************* */

        placeholders = calculatePlaceholders();


        /* *********************** */
        /* *** HEADER & FOOTER *** */
        /* *********************** */

        pageHead     = calculatePageHead();
        pageHeadBody = calculatePageHeadBody();


        /* ************* */
        /* *** COVER *** */
        /* ************* */

        cover = calculateCover();


        /* ******************************** */
        /* *** OUTPUTDEVICE AND CONTEXT *** */
        /* ******************************** */

        outputDevice = new ITextOutputDevice(dotsPerPoint);
        ClassloaderAwareUserAgent userAgent = new ClassloaderAwareUserAgent(outputDevice);

        sharedContext = new SharedContext();
        sharedContext.setUserAgentCallback(userAgent);
        sharedContext.setCss(new StyleReference(userAgent));

        userAgent.setSharedContext(sharedContext);
        outputDevice.setSharedContext(sharedContext);

        ITextFontResolver fontResolver = new ITextFontResolver(sharedContext);
        sharedContext.setFontResolver(fontResolver);

        ITextReplacedElementFactory replacedElementFactory = new ITextReplacedElementFactory(outputDevice);
        sharedContext.setReplacedElementFactory(replacedElementFactory);

        sharedContext.setTextRenderer(new ITextTextRenderer());
        sharedContext.setDPI(72 * dotsPerPoint);
        sharedContext.setDotsPerPixel(dotsPerPixel);
        sharedContext.setPrint(true);
        sharedContext.setInteractive(false);

//        sharedContext.setDebug_draw_boxes(true);
//        sharedContext.setDebug_draw_font_metrics(true);
//        sharedContext.setDebug_draw_inline_boxes(true);
//        sharedContext.setDebug_draw_line_boxes(true);

    }

    private final String evaluateAccessorConfiguration(DocetDocumentPlaceholder placeholder, String defaultValue) {
        final String value = accessor.getPlaceholderForDocument(placeholder, language);
        if (value == null) {
            return defaultValue;
        }
        return value;
    }


    private Map<HTMLPlaceholder, String> calculatePlaceholders() {

        Map<HTMLPlaceholder, String> placeholders = new EnumMap<>(HTMLPlaceholder.class);

        placeholders.put(HTMLPlaceholder.PRODUCT_NAME,
                evaluateAccessorConfiguration(DocetDocumentPlaceholder.PRODUCT_NAME, ""));

        placeholders.put(HTMLPlaceholder.PRODUCT_VERSION,
                evaluateAccessorConfiguration(DocetDocumentPlaceholder.PRODUCT_VERSION, ""));

        placeholders.put(HTMLPlaceholder.TITLE, document.getTitle());

        placeholders.put(HTMLPlaceholder.SUBTITLE,
                evaluateAccessorConfiguration(DocetDocumentPlaceholder.PDF_COVER_SUBTITLE_1, ""));


        placeholders.put(HTMLPlaceholder.FOOTER_TEXT,
                evaluateAccessorConfiguration(DocetDocumentPlaceholder.PDF_FOOTER_PAGE,
                        placeholders.get(HTMLPlaceholder.PRODUCT_NAME) + " " +
                        placeholders.get(HTMLPlaceholder.PRODUCT_VERSION)  + " - " +
                        placeholders.get(HTMLPlaceholder.TITLE)));


        placeholders.put(HTMLPlaceholder.COVER_FOOTER_TEXT,
                evaluateAccessorConfiguration(DocetDocumentPlaceholder.PDF_FOOTER_COVER, DEFAULT_FOOTER_COVER));

        placeholders.put(HTMLPlaceholder.COVER_IMAGE,
                evaluateAccessorConfiguration(DocetDocumentPlaceholder.PDF_COVER_IMAGE, DEFAULT_COVER_IMAGE));

        return placeholders;

    }

    private Element calculatePageHead() {
        Element pageHead = new Document(baseURL);
        pageHead.append("<link rel=\"stylesheet\" href=\"docetpdf-page-struct.css\" />");
        pageHead.append("<link rel=\"stylesheet\" href=\"" + css + "\" />");

        return pageHead;
    }

    private Element calculatePageHeadBody() throws DocetDocumentParsingException {
        return parseFragment("docetpdf-header-footer.html", placeholders).body();
    }


    private Document calculateCover() throws DocetDocumentParsingException {

        Document coverHead = new Document(baseURL);
        coverHead.append("<link rel=\"stylesheet\" href=\"docetpdf-cover-struct.css\" />");
        coverHead.append("<link rel=\"stylesheet\" href=\"" + css + "\" />");

//        Node bookmarkHead = generateBookmarkHead("cover",placeholders.get(HTMLPlaceholder.TITLE));
//        Node bookmarkAnchor = generateBookmarkAnchor("cover",placeholders.get(HTMLPlaceholder.TITLE));

//        coverHead.appendChild(bookmarkHead);

        Document cover = parseFragment("docetpdf-cover.html", placeholders);
        normaliseHTML(cover);

        injectHTML(cover, coverHead.childNodesCopy(), true, null, false);
//        injectHTML(cover, coverHead.childNodesCopy(), true, Collections.singletonList(bookmarkAnchor), false);

        return cover;

    }


    /* ************************* */
    /* *** PUBLIC INTERFACE  *** */
    /* ************************* */


    /**
     * <B>NOTE:</B> Caller is responsible for cleaning up the OutputStream if something goes wrong.
     */
    public void createPDF(OutputStream os) throws DocetDocumentParsingException {

        for (final SummaryEntry entry: document.getSummary()) {
            handleSummaryEntry(entry, document.getPackageName(), context, null);
        }

        createPDF(os, 1);
    }

    /* *********************** */
    /* *** PRIVATE METHODS *** */
    /* *********************** */

    private void handleSummaryEntry(
            final SummaryEntry entry,
            final String packageName,
            final DocetExecutionContext ctx,
            final DocumentPart parent) throws DocetDocumentParsingException {

        final String id = entry.getTargetPageId();
        final String lang = entry.getLang();

        final Document rawHtml;
        try {
            rawHtml = Jsoup.parse(this.manager.servePageIdForLanguageForPackage(
                    packageName, id, lang, DocetDocFormat.TYPE_PDF, false, null, ctx), "", PDFDocumentHandler.HTML_PARSER);
        } catch (DocetException e) {
            throw new DocetDocumentParsingException("Cannot retrieve page " + id, e);
        }

//        final int level = entry.getLevel();
//        for (int i = 3; i >= 1; i--) {
//            final int currentLevel = i;
//            rawHtml.select("h" + i).forEach(e -> e.tagName("h" + (currentLevel + level)));
//        }

        rawHtml.select("#main").get(0).before("<a name=\"" + id + "\"></a>");

//        final String title = summaryIndex + " " + entry.getName();
//        docTitles.add(title);
//        final PdfReader reader = new PdfReader(pdfParser.parsePage(rawHtml.toString()));
//        docsToMerge.put(title, reader);
//
//        copy.addDocument(reader);
//        reader.close();

        DocumentPart dp = generatePages(rawHtml, id, entry.getName(), parent);
        documents.add(dp);

        for (final SummaryEntry subEntry : entry.getSubSummary()) {
            handleSummaryEntry(subEntry, packageName, ctx, dp);
        }
    }

    private DocumentPart generatePages(Document document, String id, String name, DocumentPart parent) throws DocetDocumentParsingException {

        normaliseHTML(document);

//        Node bookmarkHead = generateBookmarkHead(id,name);
//        Node bookmarkAnchor = generateBookmarkAnchor(id, name);

        List<Node> head = pageHead.childNodesCopy();
//        head.add(bookmarkHead);

        List<Node> body = pageHeadBody.childNodesCopy();
//        body.add(bookmarkAnchor);

        injectHTML(document, head, true, body, false);

        DocumentPart part = generateDocumentPart(document, name, parent);

        return part;

    }

    private void writeMyBookmarks(List<DocumentPart> documents, PdfWriter writer) {

        writer.setViewerPreferences(PdfWriter.PageModeUseOutlines);

        PdfOutline root = writer.getRootOutline();

        Map<DocumentPart,PdfOutline> outlines = new HashMap<>();

        for(DocumentPart document : documents) {

            PdfOutline parent = outlines.getOrDefault(document.parent, root);

            PageBox page = document.pages.get(0);

            PdfDestination dest = new PdfDestination(PdfDestination.XYZ, 0, 0, 0);

            LOGGER.log(Level.INFO, "Writing bookmark {0} - {1} to page {2}",
                    new Object[] {this.document.getTitle(), document.name, document.startPageNo + page.getPageNo()});

            dest.addPage(writer.getPageReference(document.startPageNo + page.getPageNo()));

            PdfOutline outline = new PdfOutline(parent, dest, document.name, true);

            outlines.put(document, outline);

        }

    }

/* Original code from ITextOutputDevice.writeBookmark */
//    private void writeBookmark(RenderingContext c, Box root, PdfOutline parent, Bookmark bookmark) {
//        String href = bookmark.getHRef();
//        PdfDestination target = null;
//        Box box = bookmark.getBox();
//        if (href.length() > 0 && href.charAt(0) == '#') {
//            box = sharedContext.getBoxById(href.substring(1));
//        }
//        if (box != null) {
//            PageBox page = root.getLayer().getPage(c, getPageRefY(box));
//            int distanceFromTop = page.getMarginBorderPadding(c, CalculatedStyle.TOP);
//            distanceFromTop += box.getAbsY() - page.getTop();
//            target = new PdfDestination(PdfDestination.XYZ, 0, normalizeY(distanceFromTop / _dotsPerPoint), 0);
//            target.addPage(writer.getPageReference(startPageNo + page.getPageNo() + 1));
//        }
//        if (target == null) {
//            target = _defaultDestination;
//        }
//        PdfOutline outline = new PdfOutline(parent, target, bookmark.getName());
//        writeBookmarks(c, root, outline, bookmark.getChildren());
//    }


    private Node generateBookmarkHead(String id, String name) {
        return Parser.parseXmlFragment(
                "<bookmarks><bookmark name=\"" + name + "\" href=\"#" + id +"\" /></bookmarks>", baseURL).get(0);
    }

    private Node generateBookmarkAnchor(String id, String name) {
        return Parser.parseXmlFragment("<a name=\"" + id + "\"></a>", baseURL).get(0);
    }

    /**
     * Parse an {@code html} fragment and replace given placeholders
     */
    private Document parseFragment(
            String name,
            Map<HTMLPlaceholder,String> placeholders) throws DocetDocumentParsingException {

        String fragment;

        final InputStream is = this.getClass().getClassLoader().getResourceAsStream(name);
        try {
            try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {

                byte[] buffer = new byte[1024];
                int length;
                while ((length = is.read(buffer)) != -1) {
                    os.write(buffer, 0, length);
                }

                fragment = os.toString(StandardCharsets.UTF_8.name());

            } finally {
                is.close();
            }

        } catch (IOException e) {

            throw new DocetDocumentParsingException("Failed to load fragment " + name, e);
        }

        for(Map.Entry<HTMLPlaceholder,String> entry : placeholders.entrySet()) {
            fragment = REPLACEMENT_PATTERNS.get(entry.getKey())
                    .matcher(fragment)
                    .replaceAll(entry.getValue());
        }

        return Jsoup.parse(fragment, baseURL, HTML_PARSER);
    }

    /**
     * Externally received HTML ist just body content, we need to wrap such data into a standard html
     * structure
     *
     * @param document
     * @throws DocetDocumentParsingException
     */
    private void normaliseHTML(Document document) throws DocetDocumentParsingException {

        final List<Node> children = new ArrayList<>(document.childNodes());

        if (document.getElementsByTag("html").isEmpty()) {
            /* Plain data without HTML*/

            Element html = document.appendElement("html");
            html.appendElement("head");

            Element body = html.appendElement("body");

            for(Node child : children) {
                child.remove();
                body.appendChild(child);
            }

        }

        sanitizeHtml(document);
    }


    private void sanitizeHtml(Document doc) {

        Elements msgInUls = doc.select("ul>li>div.msg");
        for (Element msg: msgInUls) {
            Element li = msg.parent();
            Element ul = li.parent();
            msg.remove();
            li.remove();
            final Element novelUl = new Element(Tag.valueOf("ul"), "");
            novelUl.appendChild(li);
            ul.before(novelUl);
            novelUl.after(msg);
        }

//        Elements divs = doc.select(".msg");
//        for (Element div: divs) {
//            div.replaceWith(this.createElementReplacement(div));
//        }

//        Elements pres = doc.select("pre");
//        for (Element pre: pres) {
//            pre.html(pre.html().replaceAll("\n", "<br>").replaceAll("\\s", "&nbsp;"));
//            pre.replaceWith(this.createElementReplacement(pre, "pre"));
//        }

//        Elements divs = doc.select(".msg");
//        for (Element div: divs) {
//            createElementWrap(div);
//        }

//        Elements pres = doc.select("pre");
//        for (Element pre: pres) {
//            createElementWrap(replaceElement(pre,"pre")).removeClass("pre");
//        }

        Elements msgs = doc.select(".msg");
        for (Element msg : msgs) {
            msg.addClass("avoid-break");
        }

        Elements pres = doc.select("pre");
        for (Element pre: pres) {
            /* Cleanup leading and trailing spaces and newlines */
            pre.html(pre.html().trim());
            createElementWrap(pre, "pre");
//            createElementWrap(pre, "pre", "avoid-break");
        }

        Elements codes = doc.select("code");
        for (Element code: codes) {
            createElementWrap(code, "code");
        }


        Elements headings = doc.select("h1, h2, h3, h4, h5, h6");

        /* HTMLOutline.generate say that it suffice on just the html tag but isn't true */
        headings.attr("data-pdf-bookmark", "exclude");


        Elements imgs = doc.select("img:not(.inline)");
        for (Element img: imgs) {
            img.addClass("docetimage").before(new Element(Tag.valueOf("br"), "")).after(new Element(Tag.valueOf("br"), ""));
        }

//        Elements imgs = doc.select("img:not(.inline)");
//        for (Element img: imgs) {
//            img.replaceWith(this.createImageReplacement(img));
//        }
//        Elements imgsInPar = doc.select("img:not(.inline)");
//        for (Element img: imgsInPar) {
//            img.before(new Element(Tag.valueOf("br"), "")).after(new Element(Tag.valueOf("br"), ""));
//        }
    }


    private Element replaceElement(final Element toreplace, String... cssClasses) {
        final Element div = new Element(Tag.valueOf("div"), toreplace.baseUri());
        div.html(toreplace.html());
        toreplace.classNames().stream().forEach(cssClass -> div.addClass(cssClass));
        Arrays.asList(cssClasses).forEach(cssClass -> div.addClass(cssClass));
        toreplace.replaceWith(div);
        return div;
    }

    private Element createElementReplacement(final Element toreplace, String... cssClasses) {
        final Element table = new Element(Tag.valueOf("table"), "");
        final Element tr = new Element(Tag.valueOf("tr"), "");
        final Element td = new Element(Tag.valueOf("td"), "");
        td.html(toreplace.html());
        tr.appendChild(td);
        table.appendChild(tr);
        toreplace.classNames().stream().forEach(cssClass -> table.addClass(cssClass));
        Arrays.asList(cssClasses).forEach(cssClass -> table.addClass(cssClass));
        return table;
    }

    private Element createElementWrap(final Element toWrap, String... cssClasses) {
        Element wrap = toWrap.wrap("<div></div>").parent();
        toWrap.classNames().stream().forEach(cssClass -> wrap.addClass(cssClass));
        Arrays.asList(cssClasses).forEach(cssClass -> wrap.addClass(cssClass));
        return wrap;
    }

//    private Element createImageReplacement(final Element toreplace) {
//        final Element table = new Element(Tag.valueOf("table"), "");
//        final Element tr = new Element(Tag.valueOf("tr"), "");
//        final Element td = new Element(Tag.valueOf("td"), "");
//        td.html(toreplace.outerHtml());
//        tr.appendChild(td);
//        table.appendChild(tr);
//        toreplace.classNames().stream().forEach(cssClass -> table.addClass(cssClass));
//        table.addClass("docetimage");
//        return table;
//    }


    private void injectHTML(
            Document document,
            List<Node> headerHTML, boolean headerAppend,
            List<Node> bodyHTML,   boolean bodyAppend) {

        if (headerHTML != null && !headerHTML.isEmpty()) {

            Element head = document.head();

            if (headerAppend) {
                head.insertChildren(head.childNodeSize(), headerHTML);
            } else {
                head.insertChildren(0, headerHTML);
            }
        }

        if (bodyHTML != null && !bodyHTML.isEmpty()) {

            Element body = document.body();

            if (bodyAppend) {
                body.insertChildren(body.childNodeSize(), bodyHTML);
            } else {
                body.insertChildren(0, bodyHTML);
            }
        }
    }

    private static Document jsoupFromSrc(String src, String url) {
        return Jsoup.parseBodyFragment(src, url);
    }

    /**
     * This method will handle given document as is
     *
     * @param document
     * @param name
     * @param url
     * @param nsh
     * @return
     * @throws DocetDocumentParsingException
     */
    @SuppressWarnings("unchecked")
    private DocumentPart generateDocumentPart(Document document, String name, DocumentPart parent) throws DocetDocumentParsingException {

        System.out.println("---- DOCUMENT ----\n" + document);
        org.w3c.dom.Document doc = W3CDOM.fromJsoup(document);

        /* Old layout */
        getFontResolver().flushFontFaceFonts();

//        sharedContext.reset();
        if (Configuration.isTrue("xr.cache.stylesheets", true)) {
            sharedContext.getCss().flushStyleSheets();
        } else {
            sharedContext.getCss().flushAllStyleSheets();
        }

        sharedContext.setBaseURL(baseURL);
        sharedContext.setNamespaceHandler(namespaceHandler);
        sharedContext.getCss().setDocumentContext(sharedContext, sharedContext.getNamespaceHandler(), doc, NullUserInterface.INSTANCE);

        getFontResolver().importFontFaces(sharedContext.getCss().getFontFaceRules());

        LayoutContext c = newLayoutContext();
        BlockBox root = BoxBuilder.createRootBox(c, doc);
        root.setContainingBlock(new ViewportBox(getInitialExtents(c)));
        root.layout(c);

        Dimension dim = root.getLayer().getPaintingDimension(c);
        root.getLayer().trimEmptyPages(c, dim.height);

        root.getLayer().layoutPages(c);

        DocumentPart part = new DocumentPart();

        part.doc = doc;
        part.name = name;
        part.root = root;
        part.layout = c;
        part.pages = root.getLayer().getPages();

        if (parent != null) {
            part.parent = parent;
        }

        return part;

    }

    private DocumentPart generateTOC(List<DocumentPart> parts, int initialPageNo) throws DocetDocumentParsingException {

        Document document = jsoupFromSrc(buildTOC(parts, initialPageNo), baseURL);

        normaliseHTML(document);

//        Node bookmarkHead   = generateBookmarkHead("toc","Table of Contents");
//        Node bookmarkAnchor = generateBookmarkAnchor("toc","Table of Contents");

        List<Node> head = pageHead.childNodesCopy();
//        head.add(bookmarkHead);

        List<Node> body = pageHeadBody.childNodesCopy();
//        body.add(bookmarkAnchor);

        injectHTML(document, head, true, body, false);

        System.out.println(document);

        return generateDocumentPart(document, "Table of Contents", null);
    }

    private DocumentPart generateCover() throws DocetDocumentParsingException {
        return generateDocumentPart(cover, placeholders.get(HTMLPlaceholder.TITLE), null);
    }

//    private String buildTOC(List<DocumentPart> parts, int initialPageNo) {
//
//        int page = initialPageNo;
//        int capter = 1;
//
//        StringBuilder toc = new StringBuilder();
//        toc
//            .append("<div id=\"main\">")
//            .append("<h1>Table of contents</h1>")
//
//            .append("<table class=\"toc\">")
//            ;
//
//        for(DocumentPart part : parts) {
//
//            toc
//                .append("<tr>")
//                .append("<td>").append(capter++).append("</td>")
//                .append("<td>").append(part.name).append("</td>")
//                .append("<td class=\"fill\">").append("</td>")
//                .append("<td>").append(page).append("</td>")
//                .append("</tr>");
//
//            page += part.pages.size();
//        }
//
//        toc
//            .append("</table>")
//            .append("</div>")
//        ;
//
//        System.out.println(toc.toString());
//        return toc.toString();
//
//    }

    private void appendTOC(List<TOCNode> nodes, StringBuilder builder) {

        if (nodes.isEmpty()) {
            return;
        }

        builder.append("<ol>");

        for(TOCNode node : nodes) {

//            String bullet = "";
//
//            bullet = Integer.toString(node.document.getLevel());
//
//            DocumentPart parent = node.document.parent;
//            while(parent != null) {
//                bullet = Integer.toString(parent.getLevel()) + "." + bullet;
//                parent = parent.parent;
//            }

            builder
            .append("<li>")
            .append("<table class=\"l").append(node.document.getLevel()).append("\">")
            .append("<tr>")
            .append("<td class=\"toc-bullet\">").append(node.bullet).append("</td>")
            .append("<td>").append(node.document.name).append("</td>")
            .append("<td class=\"fill\">").append("</td>")
            .append("<td>").append(node.page).append("</td>")
            .append("</tr>")
            .append("</table>");

        appendTOC(node.children, builder);

        builder.append("</li>");


//            builder
//            .append("<li>")
////            .append(++count)
////            .append(". ")
//            .append(node.document.name)
//            .append(" -> ")
//            .append(node.page);
//
//        appendTOC(node.children, builder);
//
//        builder.append("</li>");

//            .append("<td>").append(++current.count).append("</td>")
//            .append("<td>").append(part.name).append("</td>")
//            .append("<td class=\"fill\">").append("</td>")
//            .append("<td>").append(page).append("</td>")

        }

        builder.append("</ol>");

    }

    private String buildTOC(List<DocumentPart> parts, int initialPageNo) {


        List<TOCNode> roots = new ArrayList<>();
        Map<DocumentPart,TOCNode> nodes = new HashMap<>();

        int pageNo = initialPageNo;
        for(DocumentPart part : parts) {

            final TOCNode parent = nodes.get(part.parent);

            if (parent == null) {

                TOCNode node = new TOCNode(part,pageNo,Integer.toString(roots.size() + 1));
                nodes.put(part, node);

                roots.add(node);

            } else {

                TOCNode node = new TOCNode(part,pageNo,parent.bullet + '.' + Integer.toString(parent.children.size() + 1));
                nodes.put(part, node);

                parent.children.add(node);
            }

            pageNo += part.pages.size();
        }


        StringBuilder builder = new StringBuilder();
        builder
            .append("<div id=\"main\">")
            .append("<h1>Table of contents</h1>")
            .append("<div id=\"toc\">");

        appendTOC(roots, builder);

        builder
            .append("</div>")
            .append("</div>")
        ;

        System.out.println(builder.toString());
        return builder.toString();

    }


    private void createPDF(OutputStream os, int initialPageNo) throws DocetDocumentParsingException {

        if (initialPageNo < 1) {
            initialPageNo = 1;
        }


        DocumentPart toc = generateTOC(documents, initialPageNo + 2 /* Cover & TOC part*/);
        /* Regenerate TOC if more than one page */
        if (toc.pages.size() > 1) {
            toc = generateTOC(documents, initialPageNo + toc.pages.size());
        }


        DocumentPart cover = generateCover();

        documents.add(0, toc);
        documents.add(0, cover);


        int totalPages = documents.stream().mapToInt(p -> p.pages.size()).sum();

        com.lowagie.text.Document pdf = null;
        PdfWriter writer = null;


        try {

            /* Uses the first cover page to evaluate page size and create the writer */

            PageBox firstPage = cover.pages.get(0);

            RenderingContext renderingContext = newRenderingContext(cover.root);
            com.lowagie.text.Rectangle firstPageSize = new com.lowagie.text.Rectangle(0, 0,
                    firstPage.getWidth(renderingContext) / dotsPerPoint,
                    firstPage.getHeight(renderingContext) / dotsPerPoint);

            pdf = new com.lowagie.text.Document(firstPageSize, 0, 0, 0, 0);

            try {
                writer = PdfWriter.getInstance(pdf, os);
            } catch (DocumentException e) {
                throw new DocetDocumentParsingException("Cannot create a pdf writer", e);
            }

            pdf.addTitle(placeholders.get(HTMLPlaceholder.TITLE));

            pdf.open();


            /* Write each document */
            try {
                for(DocumentPart part : documents) {
                    writePDF(part, totalPages, pdf, writer);
                }

            } catch (DocumentException e) {
                throw new DocetDocumentParsingException("Cannot write document " + placeholders.get(HTMLPlaceholder.TITLE) + " to pdf", e);
            }
            /* Terminate writing bookmarks with collected pages */
            writeMyBookmarks(documents, writer);

        } finally {
            if (pdf != null) {
                pdf.close();
            }

            if (writer != null) {
                writer.close();
            }
        }
    }


    private void writePDF(
            DocumentPart part,
            int totalPages,
            com.lowagie.text.Document pdf,
            PdfWriter writer) throws DocumentException {

        int initialPageNo = writer.getPageNumber();

        LOGGER.log(Level.INFO, "Writing {0} - {1}: {3} pages from {2}",
                new Object[] {document.getTitle(), part.name, initialPageNo, part.pages.size(), initialPageNo });

        part.startPageNo = initialPageNo;

        RenderingContext renderingContext = newRenderingContext(part.root);
        renderingContext.setInitialPageNo(initialPageNo);

        if (initialPageNo > 0) {
            renderingContext.setPageCount(totalPages - initialPageNo + 1);
        } else {
            renderingContext.setPageCount(totalPages);
        }


        outputDevice.setRoot(part.root);

        outputDevice.start(part.doc);
        outputDevice.setWriter(writer);
        outputDevice.setStartPageNo(initialPageNo-  1);

        part.root.getLayer().assignPagePaintingPositions(renderingContext, Layer.PAGED_MODE_PRINT);

        int pageNo = 0;
        for(PageBox page : part.pages) {

            if (Thread.currentThread().isInterrupted())
                throw new RuntimeException("Timeout occured");

            com.lowagie.text.Rectangle pageSize = new com.lowagie.text.Rectangle(0, 0,
                    page.getWidth(renderingContext) / dotsPerPoint, page.getHeight(renderingContext) / dotsPerPoint);

            pdf.setPageSize(pageSize);

            outputDevice.initializePage(writer.getDirectContent(), pageSize.getHeight());

            renderingContext.setPage(pageNo++, page);

            paintPage(renderingContext, writer, page, part.root);
            outputDevice.finishPage();

            /* Advance page */
            pdf.newPage();
        }

        outputDevice.finish(renderingContext, part.root);
        cleanOutputDevice();
    }

    /** Hack to be able to clean bookmarks */
    private static final Field BOOKMARKS_OUTPUT_DEVICE_FIELD;
    static {
        Field field = null;

        try {
            field = ITextOutputDevice.class.getDeclaredField("_bookmarks");
            field.setAccessible(true);
        } catch (NoSuchFieldException | SecurityException e) {
            LOGGER.log(Level.WARNING, "Cannot prepare ITextOutputDevice bookmarks cleaner", e);
        }

        BOOKMARKS_OUTPUT_DEVICE_FIELD = field;
    }

    /**
     * Cleanup output device for the next html page to write. This method expecially clean up bookmarks that
     * are left over in the device (or they will be printed more times).
     */
    private void cleanOutputDevice() {
        if (BOOKMARKS_OUTPUT_DEVICE_FIELD != null) {
            try {
                List<?> bookmarks = (List<?>) BOOKMARKS_OUTPUT_DEVICE_FIELD.get(outputDevice);
                bookmarks.clear();
            } catch (SecurityException | IllegalArgumentException | IllegalAccessException e) {
                LOGGER.log(Level.WARNING, "Cannot clear ITextOutputDevice bookmarks", e);
            }
        }
    }

    private ITextFontResolver getFontResolver() {
        return (ITextFontResolver) sharedContext.getFontResolver();
    }

    private Rectangle getInitialExtents(LayoutContext c) {
        PageBox first = Layer.createPageBox(c, "first");
        return new Rectangle(0, 0, first.getContentWidth(c), first.getContentHeight(c));
    }

    private RenderingContext newRenderingContext(BlockBox _root) {
        RenderingContext result = sharedContext.newRenderingContextInstance();
        result.setFontContext(new ITextFontContext());

        result.setOutputDevice(outputDevice);

        sharedContext.getTextRenderer().setup(result.getFontContext());

        result.setRootLayer(_root.getLayer());

        return result;
    }

    private LayoutContext newLayoutContext() {
        LayoutContext result = sharedContext.newLayoutContextInstance();
        result.setFontContext(new ITextFontContext());

        sharedContext.getTextRenderer().setup(result.getFontContext());

        return result;
    }

    private void paintPage(RenderingContext c, PdfWriter writer, PageBox page, BlockBox _root) {
        page.paintBackground(c, 0, Layer.PAGED_MODE_PRINT);
        page.paintMarginAreas(c, 0, Layer.PAGED_MODE_PRINT);
        page.paintBorder(c, 0, Layer.PAGED_MODE_PRINT);

        Shape working = outputDevice.getClip();

        Rectangle content = page.getPrintClippingBounds(c);
        outputDevice.clip(content);

        int top = -page.getPaintingTop() + page.getMarginBorderPadding(c, CalculatedStyle.TOP);

        int left = page.getMarginBorderPadding(c, CalculatedStyle.LEFT);

        outputDevice.translate(left, top);
        _root.getLayer().paint(c);
        outputDevice.translate(-left, -top);

        outputDevice.setClip(working);
    }

    /* *********************** */
    /* *** PRIVATE CLASSES *** */
    /* *********************** */

    private static enum HTMLPlaceholder {
        PRODUCT_NAME,
        PRODUCT_VERSION,
        TITLE,
        SUBTITLE,
        FOOTER_TEXT,
        COVER_FOOTER_TEXT,
        COVER_IMAGE;
    }

    /**
     * Working document data.
     *
     * @author diego.salvi
     */
    private static final class DocumentPart {

        private org.w3c.dom.Document doc;
        private String id;
        private String name;

        private BlockBox root;
        private List<PageBox> pages;
        private LayoutContext layout;

        private int startPageNo;

        private DocumentPart parent;
        private transient int level = 0;

        @Override
        public String toString() {
            return "DocumentPart [name=" + name + "]";
        }

        public int getLevel() {
            if (level == 0) {
                level = (parent == null? 0 : parent.getLevel() ) + 1;
            }

            return level;
        }

    }

    /** A Table of Content node */
    private static final class TOCNode {

        final DocumentPart document;

        final List<TOCNode> children;

        final String bullet;
        final int page;

        public TOCNode(DocumentPart document, int page, String bullet) {
            super();
            this.document = document;
            this.page = page;

            this.bullet = bullet;
            this.children = new ArrayList<>();
        }

    }


    /** A dummy user interface */
    private static final class NullUserInterface implements UserInterface {

        static final UserInterface INSTANCE = new NullUserInterface();

        @Override
        public boolean isHover(org.w3c.dom.Element e) {
            return false;
        }

        @Override
        public boolean isActive(org.w3c.dom.Element e) {
            return false;
        }

        @Override
        public boolean isFocus(org.w3c.dom.Element e) {
            return false;
        }
    }

    /** An user agent capable to resolve URLs from current {@link ClassLoader} */
    private static final class ClassloaderAwareUserAgent extends ITextUserAgent {

        public ClassloaderAwareUserAgent(ITextOutputDevice outputDevice) {
            super(outputDevice);
        }

        @Override
        public String resolveURI(String uri) {

            final URL resource = this.getClass().getClassLoader().getResource(uri);

            if (resource == null) {
                LOGGER.log(Level.INFO, "Resolved uri {0} externally", uri);
                return super.resolveURI(uri);
            } else {
                LOGGER.log(Level.INFO, "Resolved uri {0} internally", uri);
                return resource.toString();
            }
        }

    }

    private static final class BuilderImpl implements Builder {

        private DocetManager manager = null;
        private DocetExecutionContext context = null;

        private DocetDocumentResourcesAccessor accessor = DEFAULT_ACCESSOR;
        private DocetLanguage language = DEFAULT_LANGUAGE;

        private float dotsPerPoint = DEFAULT_DOTS_PER_POINT;
        private int dotsPerPixel = DEFAULT_DOTS_PER_PIXEL;

        private String baseURL = DEFAULT_BASE_URL;
        private NamespaceHandler namespaceHandler;

        @Override
        public Builder manager(DocetManager manager) {
            this.manager = manager;
            return this;
        }

        @Override
        public Builder context(DocetExecutionContext context) {
            this.context = context;
            return this;
        }

        @Override
        public Builder placeholders(DocetDocumentResourcesAccessor accessor) {
            this.accessor = accessor;
            return this;
        }

        @Override
        public Builder language(DocetLanguage language) {
            this.language = language;
            return this;
        }

        @Override
        public Builder dotsPerPoint(float dotsPerPoint) {
            this.dotsPerPoint = dotsPerPoint;
            return this;
        }

        @Override
        public Builder dotsPerPixel(int dotsPerPixel) {
            this.dotsPerPixel = dotsPerPixel;
            return this;
        }

        @Override
        public Builder baseURL(String baseURL) {
            this.baseURL = baseURL;
            return this;
        }

        @Override
        public Builder namespaceHandler(NamespaceHandler namespaceHandler) {
            this.namespaceHandler = namespaceHandler;
            return this;
        }

        @Override
        public PDFDocumentHandler create(DocetDocument document) throws DocetDocumentParsingException {
            if (manager == null) {
                throw new NullPointerException("Null " + DocetManager.class.getSimpleName());
            }

            if (context == null) {
                throw new NullPointerException("Null " + DocetExecutionContext.class.getSimpleName());
            }

            if (namespaceHandler == null) {
                namespaceHandler = new XhtmlNamespaceHandler();
            }

            return new PDFDocumentHandler(
                    dotsPerPoint, dotsPerPixel,
                    baseURL, namespaceHandler,
                    document, manager, context, accessor, language);
        }
    }
}
