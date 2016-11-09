package docet.maven;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.fr.FrenchAnalyzer;
import org.apache.lucene.analysis.it.ItalianAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntField;
import org.apache.lucene.document.LongField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;
import org.xml.sax.SAXException;

import docet.engine.DocetDocumentWriter;
import docet.engine.PDFDocetDocumentWriter;
import docet.engine.model.FaqEntry;
import docet.engine.model.TOC;

/**
 *
 * @author matteo.casadei
 *
 */
public final class DocetPluginUtils {

    // no. of chars to consider when extracting a sort of abstract to shown
    // later on search results.
    public static final int SHORT_SEARCH_TEXT_DEFAULT_LENGTH = 300;
    public static final int SHORT_SEARCH_ANSWER_TEXT_DEFAULT_LENGTH = 90;
    public static final String FAQ_TOC_ID = "docet-faq-menu";
    public static final String FAQ_HOME_ANCHOR_ID = "docet-faq-main-link";

    private static final String CONFIG_NAMES_FOLDER_PAGES = "pages";
    private static final String CONFIG_NAMES_FILE_TOC = "toc.html";

    private static final String ENCODING_UTF8 = "UTF-8";

    private static final int INDEX_DOCTYPE_PAGE = 1;
    private static final int INDEX_DOCTYPE_FAQ = 2;

    public enum Language {
        EN, FR, IT;

        @Override
        public String toString() {
            return super.toString().toLowerCase();
        }

        public static Language getLanguageByCode(final String code) {
            final List<Language> foundLangs = Arrays.asList(Language.values()).stream().filter(l -> l.toString().equals(code))
                .collect(Collectors.toList());
            if (foundLangs.isEmpty()) {
                return null;
            } else {
                return foundLangs.get(0);
            }
        }
    }

    private DocetPluginUtils() {
    }

    public static Map<Language, List<DocetIssue>> validateDocs(final Path srcDir, final Map<Language, List<FaqEntry>> faqs, final Log log)
        throws MojoFailureException {
        Map<Language, List<DocetIssue>> result = new EnumMap<>(Language.class);
        // checking referred pages for each doc
        for (Language lang : Language.values()) {
            Path langPath = srcDir.resolve(lang.toString());
            langPath = langPath.resolve(CONFIG_NAMES_FOLDER_PAGES);
            if (Files.exists(langPath) && Files.isDirectory(langPath)) {
                List<FaqEntry> entriesForLang = new ArrayList<>();
                faqs.put(lang, entriesForLang);
                int indexed = validateDocsForLanguage(langPath, lang, entriesForLang, (severity, msg) -> {
                    final List<DocetIssue> messages = new ArrayList<>();
                    messages.add(new DocetIssue(severity, msg));
                    result.merge(lang, messages, (l1, l2) -> {
                        l1.addAll(l2);
                        return l1;
                    });
                });
                if (indexed == 0) {
                    final List<DocetIssue> messages = new ArrayList<>();
                    messages.add(new DocetIssue(Severity.WARN, "No docs found for language"));
                    result.put(lang, messages);
                }
            } else {
                final List<DocetIssue> messages = new ArrayList<>();
                messages.add(new DocetIssue(Severity.WARN, "No folder found for language"));
                result.put(lang, messages);
            }
        }
        return result;
    }

    public static int validateDocsForLanguage(final Path path, final Language lang, final List<FaqEntry> faqs,
        final BiConsumer<Severity, String> call) throws MojoFailureException {
        final Holder<Integer> scannedDocs = new Holder<>(0);
        final Holder<Boolean> mainPageFound = new Holder<>(false);
        // the following Map is used to check duplicated titles
        final Map<String, List<String>> titleInPages = new HashMap<>();
        final Map<String, Integer> filesCount = new HashMap<>();
        final Map<String, String> foundFaqPages = new HashMap<>();
        try {
            final Path toc = path.getParent().resolve(CONFIG_NAMES_FILE_TOC);
            if (Files.exists(toc)) {
                validateToc(toc, call);
                validateFaqIndex(toc, foundFaqPages, call);
            } else {
                call.accept(Severity.ERROR, "TOC 'Table of Contents' file 'toc.html' not found!");
            }

            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(final Path file, final BasicFileAttributes attrs) throws IOException {
                    if (file.toFile().getName().startsWith(".")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                    if (file.getFileName().toString().startsWith(".")) {
                        return FileVisitResult.CONTINUE;
                    }
                    if (file.endsWith("main.html")) {
                        mainPageFound.setValue(true);
                    }
                    try {
                        validateDoc(path, file, call, titleInPages, filesCount);
                        scannedDocs.setValue(scannedDocs.getValue() + 1);
                    } catch (Exception ex) {
                        call.accept(Severity.WARN, "File " + file + " cannot be read. " + ex);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            checkForDuplicatePageTitles(titleInPages, call);
            checkForDuplicateFileNames(filesCount, call);
            final Path faqPath = path.getParent().resolve("faq");
            validateFaqs(faqPath, foundFaqPages, lang, faqs, call);
            if (!mainPageFound.getValue()) {
                call.accept(Severity.WARN, "Main page file 'main.html' not found");
            }
        } catch (Exception e) {
            throw new MojoFailureException("Failure while visiting source docs.", e);
        }
        return scannedDocs.getValue();
    }

    private static void validateFaqs(final Path faqFolderPath, final Map<String, String> faqPages, final Language lang, final List<FaqEntry> faqs,
        final BiConsumer<Severity, String> call) throws IOException {
        if (!Files.isDirectory(faqFolderPath)) {
            call.accept(Severity.WARN, "[FAQ] Directory " + faqFolderPath.toAbsolutePath() + " not found");
            return;
        }
        Files.walkFileTree(faqFolderPath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(final Path file, final BasicFileAttributes attrs) throws IOException {
                if (file.toFile().getName().startsWith(".")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                if (file.getFileName().toString().startsWith(".")) {
                    return FileVisitResult.CONTINUE;
                }
                if (faqPages.keySet().contains(file.toFile().getName())) {
                    try {
                        parseFaqEntry(file, faqPages.get(file.toFile().getName()), faqs, call);
                    } catch (Exception ex) {
                        call.accept(Severity.WARN, "FAQ File " + file + " cannot be read. " + ex);
                    }
                } else {
                    call.accept(Severity.WARN, "[FAQ] Found an unused faq file '" + file.toFile().getName() + "'. Maybe work-in-progress?");
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void parseFaqEntry(final Path file, final String faqTitle, final List<FaqEntry> faqs,
        final BiConsumer<Severity, String> call) throws IOException, SAXException, TikaException {
        // TODO validation still empty

        // add found entry to the list of pages to add to faq (on indexing/zipping stage)
        faqs.add(new FaqEntry(file, faqTitle));
    }

    private static void validateDoc(final Path rootPath, final Path file, final BiConsumer<Severity, String> call,
        final Map<String, List<String>> titleInPages, final Map<String, Integer> filesCount) 
            throws IOException, SAXException, TikaException {
        final Path pagesPath = rootPath;
        final Path imagesPath = rootPath.getParent().resolve("imgs");
        final Path faqPath = rootPath.getParent().resolve("faq");
        try (InputStream stream = Files.newInputStream(file)) {
            final String fileName = file.getFileName().toString();
            filesCount.merge(fileName, new Integer(1), (c1, c2) -> c1 + c2);
            final org.jsoup.nodes.Document htmlDoc = Jsoup.parseBodyFragment(FileUtils.readFileToString(file.toFile(), ENCODING_UTF8));

            // checking overall doc structure
            final Elements divMain = htmlDoc.select("div#main");
            switch (divMain.size()) {
                case 0:
                    call.accept(Severity.ERROR, "[" + file.getFileName() + "] Body is empty");
                    break;
                case 1:
                    break;
                default:
                    call.accept(Severity.ERROR,
                        "[" + file.getFileName() + "] Expected just one main element <div id=\"main\">, found: " + divMain.size());
            }

            // checking linked pages exists
            Elements links = htmlDoc.select("a:not(.question)");
            links.stream().forEach(link -> {
                final String href = link.attr("href");
                final boolean isFaqLink = link.hasClass("faq-link");
                final boolean pageExists;
                String pageLink = null;
                if (href.startsWith("#")) {
                    return;
                }
                try {
                    if (href.startsWith("http://") || href.startsWith("https://")) {
                        // no going to check an alleged external link
                        pageExists = true;
                        pageLink = href;
                    } else {
                        final String[] linkTokens = link.attr("href").split("/");
                        pageLink = linkTokens[linkTokens.length - 1];
                        if (pageLink.startsWith("#")) {
                            pageExists = !htmlDoc.select(pageLink).isEmpty();
                        } else if (pageLink.isEmpty()) {
                            call.accept(Severity.WARN,
                                "[" + file.getFileName() + "] [a] [id='" + link.attr("id") + "'] has no href:" + " was this done on purpose?");
                            pageExists = true;
                        } else {
                            pageExists = fileExists((isFaqLink ? faqPath : pagesPath), pageLink);
                        }
                    }
                    if (!pageExists) {
                        call.accept(Severity.ERROR, "[" + file.getFileName() + "] Referred " + (isFaqLink ? "faq" : "") + " page '" + pageLink + "' does not exist");
                    }
                } catch (IOException e) {
                    call.accept(Severity.ERROR,
                        "[" + file.getFileName() + "] Referred page '" + pageLink + "' existence cannot be checked. Reason: " + e);
                }
            });

            // checking referred images exists
            Elements images = htmlDoc.getElementsByTag("img");
            images.stream().forEach(image -> {
                final String[] linkTokens = image.attr("src").split("/");
                final String imageLink = linkTokens[linkTokens.length - 1];
                final String fileExtension = imageLink.substring(imageLink.lastIndexOf('.') + 1);
                final boolean formatAllowed = allowedFileExtension(fileExtension);
                try {
                    if (formatAllowed) {
                        final boolean imageExists = fileExists(imagesPath, imageLink);
                        if (!imageExists) {
                            call.accept(Severity.ERROR, "[" + file.getFileName() + "] Referred image '" + imageLink + "' does not exist");
                        }
                    } else {
                        call.accept(Severity.ERROR, "[" + file.getFileName() + "] Referred image '" + imageLink + "' unsupported format");
                    }
                } catch (IOException e) {
                    call.accept(Severity.ERROR,
                        "[" + file.getFileName() + "] Referred image '" + imageLink + "' existence cannot be inferred. Reason: " + e);
                }
            });

            final Elements title = htmlDoc.select("h1");
            final long foundTitles = title.stream().peek(h1 -> {
                if (h1.text().isEmpty()) {
                    call.accept(Severity.ERROR, "[" + file.getFileName() + "] Title is empty");
                }
            }).count();
            if (foundTitles > 1) {
                call.accept(Severity.ERROR, "[" + file.getFileName() + "] Found " + foundTitles + ". Just one should be provided");
            }
            if (foundTitles == 0) {
                call.accept(Severity.ERROR, "[" + file.getFileName() + "] No titles Found: 1 must be provided");
            } else if (foundTitles == 1) {
                final List<String> currentPageAsList = new ArrayList<>();
                currentPageAsList.add(file.getFileName().toString());
                // check if title has been already used on another page
                titleInPages.merge(title.text().trim(), currentPageAsList, (l1, l2) -> {
                    l1.addAll(l2);
                    return l1;
                });
            }

            // checking divs
            Elements divBoxes = htmlDoc.select("div");
            divBoxes.stream().forEach(div -> {
                // check for box div
                if (div.hasClass("msg")
                    && (!(div.hasClass("tip") || div.hasClass("info") || div.hasClass("note") || div.hasClass("warning")))) {
                        call.accept(Severity.ERROR, "[" + file.getFileName() + "] Found div.msg with not qualifying class [tip|info|note|warning]");
                }
            });
        }
    }

    private static void checkForDuplicatePageTitles(final Map<String, List<String>> titleInPages, final BiConsumer<Severity, String> call) {
        titleInPages.entrySet().stream().forEach(entry -> {
            if (entry.getValue().size() > 1) {
                call.accept(Severity.ERROR, "Title '" + entry.getKey() + "' has been used on multiple pages: "
                    + Arrays.toString(entry.getValue().toArray(new String[]{})));
            }
        });
    }

    private static void checkForDuplicateFileNames(final Map<String, Integer> pageNameCount, final BiConsumer<Severity, String> call) {
        pageNameCount.entrySet().stream().forEach(entry -> {
            if (entry.getValue() > 1) {
                call.accept(Severity.ERROR,
                    "[" + entry.getKey() + "] found " + entry.getValue() + " instances: this is going to cause linking issues!");
            }
        });
    }

    private static void validateFaqIndex(final Path faq, final Map<String, String> foundFaqs, final BiConsumer<Severity, String> call)
        throws IOException {
        try (InputStream stream = Files.newInputStream(faq)) {
            final org.jsoup.nodes.Document htmlDoc = Jsoup.parseBodyFragment(FileUtils.readFileToString(faq.toFile(), ENCODING_UTF8));
            final Elements faqItems = htmlDoc.select("#" + FAQ_TOC_ID + " a");
            if (faqItems.isEmpty()) {
                call.accept(Severity.WARN, "[FAQ] Faq list is currently empty");
                return;
            }
            faqItems.forEach(item -> {
                final String faqHref = item.attr("href");
                final Path faqItemFile = faq.getParent().resolve("faq" + File.separator + faqHref);
                if (!faqItemFile.toFile().exists()) {
                    call.accept(Severity.ERROR, "[FAQ] A file '" + faqHref + "' is linked in faq list but does not exist");
                } else {
                    foundFaqs.put(faqHref, item.text());
                }
            });
        }
    }

    private static void validateToc(final Path toc, BiConsumer<Severity, String> call) throws IOException, SAXException {
        try (InputStream stream = Files.newInputStream(toc)) {
            final org.jsoup.nodes.Document htmlDoc = Jsoup.parseBodyFragment(FileUtils.readFileToString(toc.toFile(), ENCODING_UTF8));

            // check structure
            Elements nav = htmlDoc.getElementsByTag("nav");
            final int navNum = nav.size();
            if (navNum == 0) {
                call.accept(Severity.ERROR, "[TOC] is currently empty");
            }
            if (navNum > 1) {
                call.accept(Severity.ERROR, "[TOC] defined " + navNum + " navs. One expected.");
            }

            nav.stream().forEach(n -> {
                final Elements ul = n.getElementsByTag("ul");
                final int ulNum = ul.size();
                if (ulNum == 0) {
                    call.accept(Severity.ERROR, "[TOC] is currently empty");
                } else if (ulNum > 1) {
                    call.accept(Severity.ERROR, "[TOC] nav contains " + ulNum + " ULs. One expected.");
                } else if (ulNum == 1) {
                    final Elements lis = ul.get(0).children();
                    if (lis.isEmpty()) {
                        call.accept(Severity.ERROR, "[TOC] is currently empty");
                    }
                    lis.stream().forEach(l -> {
                        if (!"li".equals(l.tag().toString())) {
                            call.accept(Severity.ERROR, "[TOC] Expected <li>, found: <" + l.tag() + ">");
                        } else {
                            final Elements anchor = l.children();
                            switch (anchor.size()) {
                                case 0:
                                    call.accept(Severity.ERROR, "[TOC] Empty <li> found");
                                    break;
                                case 1:
                                    if (!"a".equals(anchor.get(0).tag().toString())) {
                                        call.accept(Severity.ERROR, "[TOC] found <li> with no <a> included");
                                    }
                                    break;
                                case 2:
                                    if (!"a".equals(anchor.get(0).tag().toString())) {
                                        call.accept(Severity.ERROR, "[TOC] <li> -> expected <a>, found <" + anchor.get(0).tag() + ">");
                                    }
                                    if (!"ul".equals(anchor.get(1).tag().toString())) {
                                        call.accept(Severity.ERROR, "[TOC] <li> -> expected <ul>, found <" + anchor.get(1).tag() + ">");
                                    }
                                    break;
                                default:
                                    call.accept(Severity.ERROR, "[TOC] found <li> containing " + anchor.size() + " elements."
                                        + " An <li> must include no more than a <a> and a <ul>!");
                            }
                        }
                    });
                }
            });

            final Set<String> linkedPages = new HashSet<>();
            // checking linked pages exists
            final Path pagesPath = toc.getParent().resolve(CONFIG_NAMES_FOLDER_PAGES);
            final Path faqPath = toc.getParent().resolve("faq");
            Elements links = htmlDoc.getElementsByTag("a");

            Element faqMainLink = htmlDoc.getElementById(FAQ_HOME_ANCHOR_ID);
            Elements faqAnchors = new Elements();
            if (faqMainLink != null) {
                faqAnchors = faqMainLink.parent().select("ul");
                if (!faqAnchors.isEmpty()) {
                    faqAnchors = faqAnchors.select("a");
                }
            }
            final Elements faqUltimateAnchors = faqAnchors;
            links.stream().forEach(link -> {
                final String[] linkTokens = link.attr("href").split("/");
                final String pageLink = linkTokens[linkTokens.length - 1];
                boolean pageExists = false;
                boolean pageAlreadyLinked = false;
                final boolean isFaq = faqUltimateAnchors.contains(link);
                try {
                    pageExists = fileExists(isFaq ? faqPath : pagesPath, pageLink);
                    if (!pageExists) {
                        call.accept(Severity.ERROR, "[TOC] Referred " + (isFaq ? "FAQ" : "") + " page '" + pageLink + "' does not exist");
                    } else {
                        pageAlreadyLinked = !linkedPages.add(isFaq + "-" + pageLink);
                    }
                    if (pageAlreadyLinked) {
                        call.accept(Severity.ERROR, "[TOC] " + (isFaq ? "FAQ" : "") + " page '" + pageLink + "' is mentioned in TOC multiple times");
                    }
                } catch (IOException e) {
                    call.accept(Severity.ERROR, "[TOC] Referred " + (isFaq ? "FAQ" : "") + " page '" + pageLink + "' existence cannot be checked. Reason: " + e);
                }
            });
        }
    }

    private static boolean fileExists(final Path basePath, final String fileName) throws IOException {
        final Holder<Boolean> result = new Holder<>(false);
        final String actualFileName;
        if (fileName.contains("#")) {
            actualFileName = fileName.split("#")[0];
        } else {
            actualFileName = fileName;
        }
        Files.walkFileTree(basePath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                if (file.endsWith(actualFileName)) {
                    result.setValue(true);
                    return FileVisitResult.TERMINATE;
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return result.value;
    }

    private static boolean allowedFileExtension(final String fileExtension) {
        return Arrays.asList(ForbiddenExtensions.values()).stream().filter(ext -> ext.extension().equals(fileExtension)).count() == 0;
    }

    private enum ForbiddenExtensions {
        JPEG("jpeg"), JPG("jpg"), GIF("gif");

        private String extension;

        private ForbiddenExtensions(final String extension) {
            this.extension = extension;
        }

        public String extension() {
            return this.extension;
        }
    }

    public static void copyingDocs(final Path outDir, final Log log) {
        log.info("Operation will be available soon!");
        throw new UnsupportedOperationException("Operation not yet available");
    }

    public static Map<Language, List<DocetIssue>> generatePdfsForLanguage(final Path srcDir, final Path outDir, final Path tmpDir,
        final String langCode, final Log log) throws MojoFailureException {
        final Map<Language, List<DocetIssue>> result = new EnumMap<>(Language.class);
        final List<DocetIssue> messages = new ArrayList<>();
        Path langPath = srcDir.resolve(langCode);
        langPath = langPath.resolve(CONFIG_NAMES_FOLDER_PAGES);
        final Language lang = Language.getLanguageByCode(langCode);
        if (lang == null) {
            throw new MojoFailureException("Language [" + langCode + "] is not supported!");
        }
        if (Files.exists(langPath) && Files.isDirectory(langPath)) {
            final Path tocPath = langPath.getParent().resolve(CONFIG_NAMES_FILE_TOC);
            final Path outputDir = outDir;
            TOC comprenhesiveToc;
            try {
                comprenhesiveToc = parseTOCFromPath(tocPath, tmpDir, messages);
                comprenhesiveToc.getItems().stream().forEach(item -> {
                    String outFileName = Paths.get(item.getPagePath()).getFileName().toString();
                    outFileName = outFileName.replaceFirst("\\.html", "\\.pdf");
                    final File pdfFile = new File(outputDir.resolve(outFileName).toString());
                    final List<TOC.TOCItem> docToc = new ArrayList<>();
                    docToc.add(item);
                    try {
                        log.debug("reading " + item + "; generating pdf: " + pdfFile);
                        getDocetDocumentWriter().renderDocetDocument(new TOC(docToc), new FileOutputStream(pdfFile));
                    } catch (Exception e) {
                        messages.add(new DocetIssue(Severity.ERROR, "Impossible to generate pdf file. Reason: " + e));
                    }
                });
            } catch (IOException e) {
                messages.add(new DocetIssue(Severity.ERROR, "Impossible to read TOC. Reason: " + e));
            }
        } else {
            messages.add(new DocetIssue(Severity.WARN, "No folder found for language"));
        }
        result.put(lang, messages);
        return result;
    }

    private static TOC parseTOCFromPath(final Path tocFilePath, final Path tmpDir, final List<DocetIssue> messages) throws IOException {
        final TOC toc = new TOC();
        try (InputStream stream = Files.newInputStream(tocFilePath)) {
            final org.jsoup.nodes.Document htmlDoc = Jsoup.parseBodyFragment(FileUtils.readFileToString(tocFilePath.toFile(), ENCODING_UTF8));
            final Elements mainTOCItems = htmlDoc.select("nav#docet-menu>ul>li");
            mainTOCItems.forEach(el -> {
                final String fileName = el.getElementsByTag("a").get(0).attr("href");
                Path actualPagePath;
                try {
                    actualPagePath = searchFileInBasePathByName(tocFilePath.getParent().resolve(CONFIG_NAMES_FOLDER_PAGES), fileName);
                    final org.jsoup.nodes.Document sanitizedDoc = prepareHTMLForConversion(actualPagePath, tocFilePath.getParent());
                    actualPagePath = saveDocumentToDirectory(sanitizedDoc, actualPagePath.getFileName().toString(), tmpDir);
                    TOC.TOCItem item = new TOC.TOCItem(actualPagePath.toString());
                    populateTOCSubtree(tocFilePath, tmpDir, item, el.select("ul>li"), 1, messages);
                    toc.addItem(item);
                } catch (Exception e) {
                    messages.add(new DocetIssue(Severity.ERROR, "Error while parsing TOC. Reason: " + e));
                }
            });
        }
        return toc;
    }

    private static Path saveDocumentToDirectory(final org.jsoup.nodes.Document doc, final String fileName, final Path tmpDir) throws IOException {
        final Path outTmpPath = tmpDir.resolve(fileName);
        final File tmpFile = outTmpPath.toFile();
        FileUtils.writeStringToFile(tmpFile, doc.outerHtml(), ENCODING_UTF8);
        return outTmpPath;
    }

    private static void populateTOCSubtree(final Path tocFilePath, final Path tmpDir, final TOC.TOCItem item, final Elements domSubitems,
        final int level, final List<DocetIssue> messages) {
        if (domSubitems.isEmpty()) {
            return;
        }
        domSubitems.forEach(el -> {
            try {
                final String fileName = el.getElementsByTag("a").get(0).attr("href");
                Path actualPagePath = searchFileInBasePathByName(tocFilePath.getParent().resolve(CONFIG_NAMES_FOLDER_PAGES), fileName);
                final org.jsoup.nodes.Document sanitizedDoc = prepareHTMLForConversion(actualPagePath, tocFilePath.getParent());
                actualPagePath = saveDocumentToDirectory(sanitizedDoc, actualPagePath.getFileName().toString(), tmpDir);
                final TOC.TOCItem subItem = new TOC.TOCItem(actualPagePath.toString(), level);
                populateTOCSubtree(tocFilePath, tmpDir, subItem, el.select("ul>li"), level + 1, messages);
                item.addSubItem(subItem);
            } catch (Exception e) {
                messages.add(new DocetIssue(Severity.ERROR, "Error while parsing TOC. Reason: " + e));
            }
        });
    }

    private static org.jsoup.nodes.Document prepareHTMLForConversion(final Path htmlPagePath, final Path basePath) throws IOException {
        final org.jsoup.nodes.Document doc = Jsoup.parse(FileUtils.readFileToString(htmlPagePath.toFile(), ENCODING_UTF8), "", Parser.xmlParser());
        doc.getElementsByTag("head").append("<link href=\"src/docs/mndoc.css\" type=\"text/css\" rel=\"stylesheet\" />");
        final Elements images = doc.select("img");
        for (final Element img : images) {
            img.attr("src", searchFileInBasePathByName(basePath, img.attr("src")).toString());
        }
        return doc;
    }

    private static Path searchFileInBasePathByName(final Path basePath, final String fileName) throws IOException {
        final Holder<Path> result = new Holder<>(null);
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

    private static DocetDocumentWriter getDocetDocumentWriter() {
        return new PDFDocetDocumentWriter();
    }

    public static int zippingDocs(final boolean validationSkipped, final Path srcDir, final Path outDir, final Path indexDir, final boolean includeIndex, final Path zipFileName,
        final Map<Language, List<FaqEntry>> faqs, final Log log) throws MojoFailureException {
        final Holder<Integer> scannedDocs = new Holder<>(0);
        final FileToZipFilter filter = new FileToZipFilter();
        try (OutputStream fos = Files.newOutputStream(zipFileName, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            ZipOutputStream zos = new ZipOutputStream(fos);) {
            Files.walkFileTree(srcDir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(final Path file, final BasicFileAttributes attrs) throws IOException {
                    if (file.toFile().getName().startsWith(".")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                    final Path normalizedPath = srcDir.normalize().relativize(file.normalize());
                    if (file.getFileName().toString().startsWith(".")) {
                        return FileVisitResult.CONTINUE;
                    }
                    if (log.isDebugEnabled()) {
                        log.debug("Visiting file " + file.getFileName() + "; path " + normalizedPath);
                    }
                    if (filter.accept(file.toFile())) {
                        final Language lang = extractLanguageFromPath(file);
                        if (!validationSkipped && normalizedPath.toString().startsWith(lang + File.separator + "faq")) {
                            final Path fileName = normalizedPath.getFileName();
                            final List<FaqEntry> faqsForLang = faqs.get(lang);
                            if (log.isDebugEnabled() && faqsForLang != null) {
                                faqsForLang.forEach(entry -> log.debug("Filename is: " + entry.getFaqPath().getFileName()));
                            }
                            if (faqsForLang == null
                                || faqsForLang.stream().filter(entry -> entry.getFaqPath().getFileName().equals(fileName)).count() == 0) {
                                if (log.isDebugEnabled()) {
                                    log.debug("Skipping faq file " + fileName + " currently not linked");
                                }
                                return FileVisitResult.CONTINUE;
                            }
                        }

                        if (file.toFile().getName().equals(CONFIG_NAMES_FILE_TOC)) {
                            final Path tocPath = generateTocForFaq(outDir, file, lang, log);
                            writeFileToArchive(zos, srcDir.getParent().relativize(srcDir), tocPath);
                        } else {
                            writeFileToArchive(zos, srcDir.getParent().relativize(srcDir), file);
                        }
                    } else if (log.isDebugEnabled()) {
                        log.debug("Skipped " + file.getFileName());
                    }
                    scannedDocs.setValue(scannedDocs.getValue() + 1);
                    return FileVisitResult.CONTINUE;
                }
            });
            if (includeIndex) {
                Files.walkFileTree(indexDir, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                        if (file.getFileName().toString().startsWith(".")) {
                            return FileVisitResult.CONTINUE;
                        }
                        if (log.isDebugEnabled()) {
                            log.debug("Visiting index file " + file.getFileName() + "; path " + outDir.normalize().relativize(file.normalize()));
                        }
                        writeFileToArchive(zos, indexDir.getParent().relativize(indexDir), file);
                        scannedDocs.setValue(scannedDocs.getValue() + 1);
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
        } catch (IOException e) {
            throw new MojoFailureException("Error while generating zip archive: " + e, e);
        }
        return scannedDocs.getValue();
    }

    private static Language extractLanguageFromPath(final Path path) {
        final String pathRegexSeparator;
        if ("\\".equals(File.separator)) {
            pathRegexSeparator = "\\\\";
        } else {
            pathRegexSeparator = File.separator;
        }
        final String languagePathPattern = "(it|en|fr)" + pathRegexSeparator + "(faq|imgs|pages|toc\\.html)";
        Pattern pattern = Pattern.compile(languagePathPattern);
        Matcher matcher = pattern.matcher(path.toString());
        String lang = "";
        while (matcher.find()) {
            lang = matcher.group(1);
        }
        return Language.getLanguageByCode(lang);
    }

    private static Path generateTocForFaq(final Path outDir, final Path tocFile, final Language lang, final Log log)
        throws IOException {
        final Path outFaqDir = outDir.resolve(lang.toString());
        Files.createDirectories(outFaqDir);
        final Path outTocFile = outFaqDir.resolve(CONFIG_NAMES_FILE_TOC);
        try (InputStream stream = Files.newInputStream(tocFile)) {
            final org.jsoup.nodes.Document htmlDoc = Jsoup.parseBodyFragment(FileUtils.readFileToString(tocFile.toFile(), ENCODING_UTF8));
            if (log.isDebugEnabled()) {
                log.debug(htmlDoc.html());
            }
            final Element faqHomeLink = htmlDoc.getElementById(FAQ_HOME_ANCHOR_ID);
            if (faqHomeLink != null) {
                faqHomeLink.addClass("faq-a");
            }
            final Element faqTocAnchor = htmlDoc.getElementById(FAQ_TOC_ID);
            if (faqTocAnchor != null) {
                final Elements faqs = faqTocAnchor.select("a");
                faqs.stream().forEach(faq -> {
                    faq.addClass("faq-a");
                });
            }
            try (FileWriter fw = new FileWriter(outTocFile.toFile()); BufferedWriter bw = new BufferedWriter(fw);) {
                bw.write(htmlDoc.html());
            }
        }
        return outTocFile;

    }

    private static void writeFileToArchive(final ZipOutputStream zos, final Path baseSrcPath, final Path filePath) throws IOException {
        final byte[] buffer = new byte[1024];
        final String zipPath = baseSrcPath.resolve(extractLanguageRelativePath(filePath)).toString();
        final ZipEntry ze = new ZipEntry(zipPath);
        zos.putNextEntry(ze);
        try (FileInputStream in = new FileInputStream(filePath.toFile());) {
            int len;
            while ((len = in.read(buffer)) > 0) {
                zos.write(buffer, 0, len);
            }
        }
        zos.closeEntry();
    }

    private static String extractLanguageRelativePath(final Path absolutePath) {
        final String fileName = absolutePath.getFileName().toString();
        final String pathRegexSeparator;
        if ("\\".equals(File.separator)) {
            pathRegexSeparator = "\\\\";
        } else {
            pathRegexSeparator = File.separator;
        }
        final String languagePathPattern = "(it|en|fr)" + pathRegexSeparator + "(faq|imgs|pages|toc\\.html)";
        Pattern pattern = Pattern.compile(languagePathPattern);
        Matcher matcher = pattern.matcher(absolutePath.toString());
        String lang = "";
        String folderType = "";
        while (matcher.find()) {
            lang = matcher.group(1);
            folderType = matcher.group(2);
        }
        String result = lang + File.separator + folderType;
        if (result.endsWith(File.separator)) {
            result = fileName;
        } else if (!fileName.equals(CONFIG_NAMES_FILE_TOC)) {
            result += File.separator + fileName;
        }
        return result;
    }

    public static void indexDocs(final Path outDir, final Path srcDir, final Map<Language, List<FaqEntry>> faqs, final Log log)
        throws MojoFailureException {
        for (Language lang : Language.values()) {
            Path langPath = srcDir.resolve(lang.toString());
            langPath = langPath.resolve(CONFIG_NAMES_FOLDER_PAGES);
            if (Files.exists(langPath) && Files.isDirectory(langPath)) {
                indexDocsForLanguage(outDir, langPath, lang, faqs.get(lang), log);
            } else {
                log.warn("[" + lang + "] No folder found for language");
            }
        }
    }

    public static int indexDocsForLanguage(final Path outDir, final Path path, final Language lang, final List<FaqEntry> faqs, final Log log)
        throws MojoFailureException {
        final Holder<Integer> indexedDocs = new Holder<>(0);
        try (Directory dir = FSDirectory.open(outDir);) {
            //build the analyzer specific for the given language
            //Stadard analyzer if language is not supported
            Analyzer analyzer = new AnalyzerBuilder().language(lang).build();
            IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
            iwc.setOpenMode(OpenMode.CREATE_OR_APPEND);

            // Optional: for better indexing performance, if you
            // are indexing many documents, increase the RAM
            // buffer. But if you do this, increase the max heap
            // size to the JVM (eg add -Xmx512m or -Xmx1g):
            //
            // iwc.setRAMBufferSizeMB(256.0);
            try (IndexWriter writer = new IndexWriter(dir, iwc);) {
                log.info("[" + lang + "] Building search index for language");
                Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult preVisitDirectory(final Path file, final BasicFileAttributes attrs) throws IOException {
                        if (file.toFile().getName().startsWith(".")) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                        if (file.getFileName().toString().startsWith(".")) {
                            return FileVisitResult.CONTINUE;
                        }
                        log.debug("Visiting " + file);
                        try {
                            indexDoc(writer, file, attrs.lastModifiedTime().toMillis(), lang.toString(), log);
                            indexedDocs.setValue(indexedDocs.getValue() + 1);
                        } catch (Exception ex) {
                            log.warn("[" + lang + "] File " + file + " cannot be read.", ex);
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
                // indexing faqs
                Optional.ofNullable(faqs).orElseGet(() -> new ArrayList<>()).stream().forEach(faqPage -> {
                    try {
                        final Path faqFile = faqPage.getFaqPath();
                        indexFaqPage(writer, faqFile, faqPage.getTitle(), faqFile.toFile().lastModified(), lang.toString(), log);
                        indexedDocs.setValue(indexedDocs.getValue() + 1);
                    } catch (Exception ex) {
                        log.warn("[" + lang + "] FAQ File " + faqPage.getFaqPath() + " cannot be read.", ex);
                    }
                });

                writer.forceMerge(1);
                writer.commit();
                if (indexedDocs.getValue() == 0) {
                    log.warn("[" + lang + "] \t -> No docs found for language");
                }
            } catch (IOException e) {
                throw new MojoFailureException("Impossible to index Docet docs.", e);
            }
        } catch (IOException e) {
            throw new MojoFailureException("Impossible to index Docet docs.", e);
        }
        return indexedDocs.getValue();
    }

    /**
     *
     * @param file
     * @param lastModified
     * @param lang
     * @param docTitle
     * @param docAbstract
     * @param docType
     *
     * @throws IOException
     * @throws SAXException
     * @throws TikaException
     */
    private static void indexGenericDoc(final IndexWriter writer, final Path file, final long lastModified, final String lang, final String docTitle,
        final String docAbstract, final int docType, final Log log) throws IOException, SAXException, TikaException {
        Document doc = new Document();
        try (InputStream stream = Files.newInputStream(file)) {
            Field pathField = new StringField("path", file.toString(), Field.Store.YES);
            doc.add(pathField);
            doc.add(new LongField("modified", lastModified, Field.Store.NO));
            doc.add(new TextField("contents-" + lang, convertDocToText(stream), Field.Store.YES));
            doc.add(new StringField("language", lang, Field.Store.YES));
            doc.add(new StringField("id", constructPageIdFromFilePath(file), Field.Store.YES));
            doc.add(new StringField("title", docTitle, Field.Store.YES));
            doc.add(new IntField("doctype", docType, Field.Store.YES));
            doc.add(new TextField("abstract", docAbstract, Field.Store.YES));
        }

        if (writer.getConfig().getOpenMode() == OpenMode.CREATE) {
            log.info("[" + lang + "] Adding " + file + " to index");
            log.debug("document: " + doc);
            writer.addDocument(doc);
        } else {
            log.debug("[" + lang + "] Updating " + file + " to index");
            writer.updateDocument(new Term("path", file.toString()), doc);
        }
    }

    /**
     * Indexes a single document.
     *
     * @throws TikaException
     * @throws SAXException
     * @throws IOException
     *
     */
    private static void indexDoc(final IndexWriter writer, final Path file, final long lastModified, final String lang, final Log log)
        throws IOException, SAXException, TikaException {
        String docTitle = "";
        String excerpt = "...";
        try (InputStream stream = Files.newInputStream(file)) {
            final org.jsoup.nodes.Document htmlDoc = Jsoup.parseBodyFragment(FileUtils.readFileToString(file.toFile(), ENCODING_UTF8));
            docTitle = htmlDoc.getElementsByTag("h1").get(0).text();

            // conventionally take the first <p> and treat it as an abstract.
            final Elements pars = htmlDoc.select("div#main p#abstract");
            if (!pars.isEmpty()) {
                final Element abstractPar = pars.get(0);
                final String firstPar = abstractPar.text();

                final String abstractText;
                if (firstPar != null && !firstPar.isEmpty()) {
                    abstractText = firstPar;
                } else {
                    // was not possible to find an abstract
                    abstractText = "";
                }

                if (abstractText.length() <= SHORT_SEARCH_TEXT_DEFAULT_LENGTH) {
                    excerpt = abstractText;
                } else {
                    excerpt = abstractText.substring(0, SHORT_SEARCH_TEXT_DEFAULT_LENGTH) + "...";
                }
            }
        }
        indexGenericDoc(writer, file, lastModified, lang, docTitle, excerpt, INDEX_DOCTYPE_PAGE, log);
    }

    /**
     * Indexes a single faq page.
     *
     * @throws TikaException
     * @throws SAXException
     * @throws IOException
     *
     */
    private static void indexFaqPage(final IndexWriter writer, final Path file, final String faqTitle, final long lastModified, final String lang,
        final Log log) throws IOException, SAXException, TikaException {
        String excerpt = "";
        final StringBuilder excerptBuilder = new StringBuilder();
        try (InputStream stream = Files.newInputStream(file)) {
            final org.jsoup.nodes.Document htmlDoc = Jsoup.parseBodyFragment(FileUtils.readFileToString(file.toFile(), ENCODING_UTF8));

            // conventionally take the first <p> and treat it as an abstract.
            final Elements pars = htmlDoc.select("div.faq-item");
            for (Element faq : pars) {
                final Elements questions = faq.select(".question");
                final Elements answers = faq.select(".answer");
                String question = "";
                // TODO to remove once validation is implemented
                if (!questions.isEmpty()) {
                    question = "<b> " + questions.get(0).text() + "</b><br/>";
                }
                String answer = "";
                if (!answers.isEmpty()) {
                    String rawAnswer = answers.get(0).text();
                    if (rawAnswer.length() > SHORT_SEARCH_ANSWER_TEXT_DEFAULT_LENGTH) {
                        rawAnswer = rawAnswer.substring(0, SHORT_SEARCH_ANSWER_TEXT_DEFAULT_LENGTH) + "...";
                    }
                    answer = rawAnswer;
                }
                excerptBuilder.append(question).append(answer).append("<br/>");
                excerpt = excerptBuilder.toString();
                if (excerpt.length() >= SHORT_SEARCH_TEXT_DEFAULT_LENGTH) {
                    break;
                }
            }
        }
        indexGenericDoc(writer, file, lastModified, lang, "FAQ - " + faqTitle, excerpt, INDEX_DOCTYPE_FAQ, log);

    }

    private static String getFileSeparatorForString() {
        final String separatorStr;
        if ("\\".equals(File.separator)) {
            separatorStr = "\\\\";
        } else {
            separatorStr = "/";
        }
        return separatorStr;
    }

    private static String constructPageIdFromFilePath(final Path file) {
        final String[] tokens = file.toString().split(getFileSeparatorForString());
        final String fileName = tokens[tokens.length - 1];
        return fileName.split(".html")[0];
    }

    public static class Holder<T> {

        private T value;

        Holder(T value) {
            setValue(value);
        }

        T getValue() {
            return value;
        }

        void setValue(T value) {
            this.value = value;
        }
    }

    private static String convertDocToText(final InputStream streamDoc) throws IOException, SAXException, TikaException {
        AutoDetectParser parser = new AutoDetectParser();
        BodyContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();
        parser.parse(streamDoc, handler, metadata);
        return handler.toString();
    }

    private static class FileToZipFilter implements FileFilter {

        private final String[] toSkipExtensions = new String[]{"css"};

        @Override
        public boolean accept(final File file) {
            for (final String extension : toSkipExtensions) {
                if (file.getName().toLowerCase().endsWith(extension)) {
                    return false;
                }
            }
            return true;
        }
    }

    private static class AnalyzerBuilder {

        private Language lang;

        public AnalyzerBuilder() {
            this.lang = Language.IT;
        }

        public AnalyzerBuilder language(final Language lang) {
            this.lang = lang;
            return this;
        }

        public Analyzer build() {
            final Analyzer analyzer;
            switch (this.lang) {
                case FR:
                    analyzer = new FrenchAnalyzer();
                    break;
                case IT:
                    analyzer = new ItalianAnalyzer();
                    break;
                case EN:
                default:
                    analyzer = new StandardAnalyzer();
            }
            return analyzer;
        }
    }
}
