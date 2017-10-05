package docet.maven;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
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
import org.jsoup.nodes.Node;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;
import org.xml.sax.SAXException;

import docet.engine.model.FaqEntry;
import docet.engine.model.TOC;

/**
 *
 * @author matteo.casadei
 *
 */
public final class DocetPluginUtils {

    public static final String FAQ_DEFAULT_PAGE_PREFIX = "faq_";
    // no. of chars to consider when extracting a sort of abstract to shown
    // later on search results.
    public static final int SHORT_SEARCH_TEXT_DEFAULT_LENGTH = 300;
    public static final int SHORT_SEARCH_ANSWER_TEXT_DEFAULT_LENGTH = 90;
    public static final String FAQ_TOC_ID = "docet-faq-menu";
    public static final String FAQ_HOME_ANCHOR_ID = "docet-faq-main-link";

    private static final String CONFIG_NAMES_FOLDER_PAGES = "pages";
    private static final String CONFIG_NAMES_FOLDER_PDFS = "pdf";
    private static final String CONFIG_NAMES_FOLDER_IMAGES = "imgs";
    private static final String CONFIG_NAMES_FILE_TOC = "toc.html";
    private static final String DOCET_HTML_ATTR_REFERENCE_LANGUAGE_NAME = "reference-language";

    private static final Charset ENCODING_UTF8 = StandardCharsets.UTF_8;

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
                }, log);
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

    public static Map<Language, List<DocetIssue>> validatePdfs(final Path srcDir, final Log log)
        throws MojoFailureException {
        Map<Language, List<DocetIssue>> result = new EnumMap<>(Language.class);
        // checking referred pages for each doc
        for (Language lang : Language.values()) {
            Path langPath = srcDir.resolve(lang.toString());
            langPath = langPath.resolve(CONFIG_NAMES_FOLDER_PDFS);
            if (Files.exists(langPath) && Files.isDirectory(langPath)) {
                int indexed = validatePdfsForLanguage(langPath, lang, (severity, msg) -> {
                    final List<DocetIssue> messages = new ArrayList<>();
                    messages.add(new DocetIssue(severity, msg));
                    result.merge(lang, messages, (l1, l2) -> {
                        l1.addAll(l2);
                        return l1;
                    });
                }, log);
                if (indexed == 0) {
                    final List<DocetIssue> messages = new ArrayList<>();
                    messages.add(new DocetIssue(Severity.WARN, "No pdfs found for language"));
                    result.put(lang, messages);
                }
            } else {
                final List<DocetIssue> messages = new ArrayList<>();
                messages.add(new DocetIssue(Severity.WARN, "No pdfs found for language"));
                result.put(lang, messages);
            }
        }
        return result;
    }

    private static Set<String> retrieveImageNames(final Path imgsFolder, final BiConsumer<Severity, String> call,
        final Log log) throws IOException {
        final Set<String> res = new HashSet<>();

        if (!Files.isDirectory(imgsFolder)) {
            call.accept(Severity.WARN, "[IMGS] Directory " + imgsFolder.toAbsolutePath() + " not found");
            return res;
        }

        Files.walkFileTree(imgsFolder, new SimpleFileVisitor<Path>() {
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
                final String fileName = file.toFile().getName();
                if (log.isDebugEnabled()) {
                    log.debug("[retrieveImageNames] add image file " + fileName + " to set of found images");
                }
                res.add(fileName);
                return FileVisitResult.CONTINUE;
            }
        });
        return res;
    }

    public static int validatePdfsForLanguage(final Path path, final Language lang,
        final BiConsumer<Severity, String> call, final Log log) throws MojoFailureException {
        final Holder<Integer> scannedDocs = new Holder<>(0);
        try {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(final Path file, final BasicFileAttributes attrs)
                    throws IOException {
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
                    try {
                        validatePdf(file, call);
                        scannedDocs.setValue(scannedDocs.value + 1);
                    } catch (Exception ex) {
                        call.accept(Severity.ERROR, "File " + file + " cannot be read. " + ex);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            return scannedDocs.value;
        } catch (Exception e) {
            throw new MojoFailureException("Failure while visiting source docs.", e);
        }
    }

    public static int validateDocsForLanguage(final Path path, final Language lang, final List<FaqEntry> faqs,
        final BiConsumer<Severity, String> call, final Log log) throws MojoFailureException {
        final Holder<Integer> scannedDocs = new Holder<>(0);
        final Holder<Boolean> mainPageFound = new Holder<>(false);
        // the following Map is used to check duplicated titles
        final Map<String, List<String>> titleInPages = new HashMap<>();
        final Map<String, Integer> filesCount = new HashMap<>();
        final Map<String, String> foundFaqPages = new HashMap<>();
        try {
            final Path toc = path.getParent().resolve(CONFIG_NAMES_FILE_TOC);
            final Map<String, String> linkedPagesInToc = new HashMap<>();
            if (Files.exists(toc)) {
                validateToc(toc, linkedPagesInToc, call);
                validateFaqIndex(toc, foundFaqPages, linkedPagesInToc, call);
            } else {
                call.accept(Severity.ERROR, "TOC 'Table of Contents' file 'toc.html' not found!");
            }

            final Path imgs = path.getParent().resolve(CONFIG_NAMES_FOLDER_IMAGES);
            final Set<String> foundImages = retrieveImageNames(imgs, call, log);
            final Set<String> imagesLinkedInPages = new HashSet<>();

            if (log.isDebugEnabled()) {
                linkedPagesInToc.keySet().stream().forEach(item -> {
                    log.debug("[" + lang + "] LINKED PAGE FOUND -> '" + item + "'");
                });
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
                        linkedPagesInToc.put("main.html", "Main page");
                    }
                    try {
                        final String fileName = file.toFile().getName();
                        if (linkedPagesInToc.get(fileName) != null) {
                            validateDoc(path, file, call, titleInPages, imagesLinkedInPages, filesCount);
                            scannedDocs.setValue(scannedDocs.getValue() + 1);
                        } else {
                            call.accept(Severity.ERROR, "NON-LINKED (UNUSED) FILE FOUND: " + fileName);
                        }
                    } catch (Exception ex) {
                        call.accept(Severity.ERROR, "File " + file + " cannot be read. " + ex);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            checkForDuplicatePageTitles(titleInPages, call);
            checkForDuplicateFileNames(filesCount, call);
            checkForOrphanImages(foundImages, imagesLinkedInPages, call);
            final Path faqPath = path.getParent().resolve("faq");
            validateFaqs(faqPath, faqs, call, foundFaqPages, linkedPagesInToc, false);
            validateFaqs(faqPath, faqs, call, foundFaqPages, linkedPagesInToc, true);
            checkForOrphanFaqLinks(faqs, foundFaqPages.keySet(), call);
            if (!mainPageFound.getValue()) {
                call.accept(Severity.WARN, "Main page file 'main.html' not found");
            }

            linkedPagesInToc.keySet().stream().filter(pageName -> pageName.startsWith(FAQ_DEFAULT_PAGE_PREFIX))
                .forEach(pageName -> {
                    call.accept(Severity.ERROR, "NON-LINKED (UNUSED) FAQ FILE FOUND: " + pageName);
                });
        } catch (Exception e) {
            throw new MojoFailureException("Failure while visiting source docs.", e);
        }
        return scannedDocs.getValue();
    }

    /**
     * Checks whether an image in file system is linked from at least one page within the source page files.
     *
     * @param imgsInFileSystem the set of images found on the doc package file system
     * @param imgsLinked the set of images linked from source page files
     * @param issueLogger consumer used to keep track of issues found on the aforemetioned checks
     */
    private static void checkForOrphanImages(final Set<String> imgsInFileSystem, final Set<String> imgsLinked,
        final BiConsumer<Severity, String> issueLogger) {
        final Set<String> linkedImgsNames = imgsLinked.stream()
            .map(img -> img.split("@")[0])
            .collect(Collectors.toSet());

        //check for unused images
        imgsInFileSystem.stream()
            .filter(img -> !linkedImgsNames.contains(img))
            .forEach(img -> issueLogger.accept(Severity.ERROR, "[ORPHAN IMAGE] Found UNUSED IMAGE " + img));
    }

    private static void checkForOrphanFaqLinks(final List<FaqEntry> faqsToAdd, final Set<String> faqsWithLinks,
        final BiConsumer<Severity, String> issueCall) {
        //checking that all the faqslinks point to faq pages present in the list of faqentry to be included in the doc
        //package
        final List<String> faqsToAddNames = faqsToAdd.stream().map(entry -> entry.getFaqPath().toFile().getName())
            .collect(Collectors.toList());
        faqsWithLinks.stream().filter(faq -> !faqsToAddNames.contains(faq.split("@")[0]))
            .collect(Collectors.toList()).forEach(orphan -> {
            final String[] tokens = orphan.split("@");
            final String srcPage = tokens[1];
            final String targetFaqPage = tokens[0];
            issueCall.accept(Severity.ERROR, "[FAQ] [ORPHAN LINK] [" + srcPage + "] links to unexisting faq -> " + targetFaqPage);
        });
    }

    private static void validateFaqs(final Path faqFolderPath, final List<FaqEntry> faqs, final BiConsumer<Severity, String> call, final Map<String, String> faqPages, final Map<String, String> pagesFoundInTOC, boolean generateEntries)
        throws IOException {
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

                final String fileName = file.getFileName().toString();

                final org.jsoup.nodes.Document htmlDoc = Jsoup.parseBodyFragment(readAll(file, ENCODING_UTF8));

                // checking linked pages exists
                Elements links = htmlDoc.select("a.faq-link");
                links.stream().forEach(link -> {
                    faqPages.put(link.attr("href").split("#")[0] + "@" + fileName, link.text());
                });

                if (generateEntries) {
                    //if this is a first-level faq the corresponding name should be found in toc
                    //if so delete the corresponding name from the set so that is not be counted as not found 1st-level
                    //faq
                    final String title = pagesFoundInTOC.remove(FAQ_DEFAULT_PAGE_PREFIX + fileName);
                    final boolean found = faqPages.keySet().stream().anyMatch(faq -> faq.startsWith(fileName + "@"));
                    if (found || title != null) {
                        final String faqTitle;
                        if (title != null) {
                            faqTitle = title;
                        } else {
                            faqTitle = faqPages.entrySet().stream()
                                .filter(e -> e.getKey().startsWith(fileName))
                                .findFirst().map(e -> e.getValue()).orElse("");
                        }
                        try {
                            parseFaqEntry(file, faqTitle, faqs, call);
                        } catch (Exception ex) {
                            call.accept(Severity.WARN, "FAQ File " + file + " cannot be read. " + ex);
                        }
                    } else {
                        call.accept(Severity.ERROR, "[FAQ] Found an unused faq file '" + file + "'");
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void parseFaqEntry(final Path file, final String faqTitle, final List<FaqEntry> faqs,
        final BiConsumer<Severity, String> call) throws IOException, SAXException, TikaException {
        // Faq page validation
        final Path pagesPath = file.getParent().getParent().resolve(CONFIG_NAMES_FOLDER_PAGES);;
        final Path pdfPath = pagesPath.getParent().resolve(CONFIG_NAMES_FOLDER_PDFS);
        final Path imagesPath = pagesPath.getParent().resolve("imgs");
        final Path faqPath = pagesPath.getParent().resolve("faq");

            final org.jsoup.nodes.Document htmlDoc = Jsoup.parseBodyFragment(readAll(file, ENCODING_UTF8));

            // checking overall doc structure
            final Elements divMain = htmlDoc.select("div#main");
            switch (divMain.size()) {
                case 0:
                call.accept(Severity.ERROR, "[FAQ] [" + file.getFileName() + "] Body is empty");
                break;
            case 1:
                break;
            default:
                call.accept(Severity.ERROR,
                    "[FAQ] [" + file.getFileName() + "] Expected just one main element <div id=\"main\">, found: " + divMain.size());
        }

        // checking linked pages exists
        Elements links = htmlDoc.select("a:not(.question)");
        links.stream().forEach(link -> {
            if (!link.hasAttr("href")) {
                call.accept(Severity.ERROR,
                    "[FAQ] [" + file.getFileName() + "] Found anchor without HREF attribute '" + link + "'");
                return;
            }
            final String href = link.attr("href");
            final boolean isFaqLink = link.hasClass("faq-link");
            final boolean pageExists;
            final boolean isPdfLink;
            String pageLink = null;
            if (href.startsWith("#")) {
                return;
            }
            try {
                if (href.startsWith("http://") || href.startsWith("https://")) {
                    // no going to check an alleged external link
                    pageExists = true;
                    pageLink = href;
                    isPdfLink = false;
                } else {
                    final String[] linkTokens = link.attr("href").split("/");
                    pageLink = linkTokens[linkTokens.length - 1];
                    if (pageLink.startsWith("#")) {
                        pageExists = !htmlDoc.select(pageLink).isEmpty();
                        isPdfLink = false;
                    } else if (pageLink.isEmpty()) {
                        call.accept(Severity.WARN,
                            "[FAQ] [" + file.getFileName() + "] Found anchor '" + link + "' WITH EMPTY HREF:" + " was this done on purpose?");
                        pageExists = true;
                        isPdfLink = false;
                    } else if (pageLink.endsWith(".pdf")) {
                        isPdfLink = true;
                        pageExists = fileExists(pdfPath, pageLink.replace(".pdf", ".html"));
                    } else {
                        isPdfLink = false;
                        pageExists = fileExists((isFaqLink ? faqPath : pagesPath), pageLink);
                    }
                }
                if (!pageExists) {
                    if (isPdfLink) {
                        call.accept(Severity.ERROR, "[FAQ] [" + file.getFileName() + "] Referred pdf '" + pageLink + "' does not exist");
                    } else {
                        call.accept(Severity.ERROR, "[FAQ] [" + file.getFileName() + "] Referred " + (isFaqLink ? "faq" : "") + " page '" + pageLink + "' does not exist");
                    }
                }
            } catch (IOException e) {
                call.accept(Severity.ERROR,
                    "[FAQ] [" + file.getFileName() + "] Referred page '" + pageLink + "' existence cannot be checked. Reason: " + e);
            }
        });

        // checking referred images exists
        Elements images = htmlDoc.getElementsByTag("img");
        images.stream().forEach(image -> {
            if (!image.hasAttr("src")) {
                call.accept(Severity.ERROR,
                    "[FAQ] [" + file.getFileName() + "] Found image without SRC attribute '" + image + "'");
                return;
            }
            final String[] linkTokens = image.attr("src").split("/");
            final String imageLink = linkTokens[linkTokens.length - 1];
            final String fileExtension = imageLink.substring(imageLink.lastIndexOf('.') + 1);
            final boolean formatAllowed = allowedFileExtension(fileExtension);
            try {
                if (formatAllowed) {
                    final boolean imageExists = fileExists(imagesPath, imageLink);
                    if (!imageExists) {
                        call.accept(Severity.ERROR, "[FAQ] [" + file.getFileName() + "] Referred image '" + imageLink + "' does not exist");
                    }
                } else {
                    call.accept(Severity.ERROR, "[FAQ] [" + file.getFileName() + "] Referred image '" + imageLink + "' unsupported format");
                }
            } catch (IOException e) {
                call.accept(Severity.ERROR,
                    "[FAQ] [" + file.getFileName() + "] Referred image '" + imageLink + "' existence cannot be inferred. Reason: " + e);
            }
        });

        final Elements title = htmlDoc.select("h1");
        final long foundTitles = title.stream().peek(h1 -> {
            if (h1.text().isEmpty()) {
                call.accept(Severity.ERROR, "[FAQ] [" + file.getFileName() + "] Title is empty");
            }
        }).count();
        if (foundTitles > 1) {
            call.accept(Severity.ERROR, "[FAQ] [" + file.getFileName() + "] Found " + foundTitles + ". Just one should be provided");
        }
        if (foundTitles == 0) {
            call.accept(Severity.ERROR, "[FAQ] [" + file.getFileName() + "] No titles Found: 1 must be provided");
        }

        // checking divs
        Elements divBoxes = htmlDoc.select("div");
        divBoxes.stream().forEach(div -> {
            // check for box div
            if (div.hasClass("msg")
                && (!(div.hasClass("tip") || div.hasClass("info") || div.hasClass("note") || div.hasClass("warning")))) {
                call.accept(Severity.ERROR, "[FAQ] [" + file.getFileName() + "] Found div.msg with not qualifying class [tip|info|note|warning]");
            }
        });

        // add found entry to the list of pages to add to faq (on indexing/zipping stage)
        faqs.add(new FaqEntry(file, faqTitle));
    }

    private static void validateDoc(final Path rootPath, final Path file, final BiConsumer<Severity, String> call,
        final Map<String, List<String>> titleInPages, final Set<String> linkedImages,
        final Map<String, Integer> filesCount)
        throws IOException, SAXException, TikaException {
        final Path pagesPath = rootPath;
        final Path pdfPath = rootPath.getParent().resolve(CONFIG_NAMES_FOLDER_PDFS);
        final Path imagesPath = rootPath.getParent().resolve("imgs");
        final Path faqPath = rootPath.getParent().resolve("faq");

        final String fileName = file.getFileName().toString();
        filesCount.merge(fileName, Integer.valueOf(1), (c1, c2) -> c1 + c2);
        final org.jsoup.nodes.Document htmlDoc = Jsoup.parseBodyFragment(readAll(file, ENCODING_UTF8));

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
            if (!link.hasAttr("href")) {
                call.accept(Severity.ERROR,
                    "[" + file.getFileName() + "] Found anchor without HREF attribute '" + link + "'");
                return;
            }
            final String href = link.attr("href");
            final boolean isFaqLink = link.hasClass("faq-link");
            final boolean pageExists;
            final boolean isPdfLink;
            String pageLink = null;
            if (href.startsWith("#")) {
                return;
            }
            try {
                if (href.startsWith("http://") || href.startsWith("https://")) {
                    // no going to check an alleged external link
                    pageExists = true;
                    pageLink = href;
                    isPdfLink = false;
                } else {
                    final String[] linkTokens = link.attr("href").split("/");
                    pageLink = linkTokens[linkTokens.length - 1];
                    if (pageLink.startsWith("#")) {
                        pageExists = !htmlDoc.select(pageLink).isEmpty();
                        isPdfLink = false;
                    } else if (pageLink.isEmpty()) {
                        call.accept(Severity.WARN,
                            "[" + file.getFileName() + "] Found anchor '" + link + "' WITH EMPTY HREF:" + " was this done on purpose?");
                        pageExists = true;
                        isPdfLink = false;
                    } else if (pageLink.endsWith(".pdf")) {
                        isPdfLink = true;
                        pageExists = fileExists(pdfPath, pageLink.replace(".pdf", ".html"));
                    } else {
                        isPdfLink = false;
                        pageExists = fileExists((isFaqLink ? faqPath : pagesPath), pageLink);
                    }
                }
                if (!pageExists) {
                    if (isPdfLink) {
                        call.accept(Severity.ERROR, "[" + file.getFileName() + "] Referred pdf '" + pageLink + "' does not exist");
                    } else {
                        call.accept(Severity.ERROR, "[" + file.getFileName() + "] Referred " + (isFaqLink ? "faq" : "") + " page '" + pageLink + "' does not exist");
                    }
                }
            } catch (IOException e) {
                call.accept(Severity.ERROR,
                    "[" + file.getFileName() + "] Referred page '" + pageLink + "' existence cannot be checked. Reason: " + e);
            }
        });

        // checking referred images exists
        Elements images = htmlDoc.getElementsByTag("img");
        images.stream().forEach(image -> {
            if (!image.hasAttr("src")) {
                call.accept(Severity.ERROR,
                    "[" + file.getFileName() + "] Found image without SRC attribute '" + image + "'");
                return;
            }
            final String[] linkTokens = image.attr("src").split("/");
            final String imageLink = linkTokens[linkTokens.length - 1];
            linkedImages.add(imageLink + "@" + fileName);
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

    private static void validateFaqIndex(final Path faq, final Map<String, String> foundFaqs,
        final Map<String, String> foundLinkedFaqs, final BiConsumer<Severity, String> call)
        throws IOException {
        final org.jsoup.nodes.Document htmlDoc = Jsoup.parseBodyFragment(readAll(faq, ENCODING_UTF8));
        final Elements faqItems = htmlDoc.select("#" + FAQ_TOC_ID + " a");
        if (faqItems.isEmpty()) {
            call.accept(Severity.WARN, "[TOC] NO FAQs defined in table of contents");
            return;
        }
        faqItems.forEach(item -> {
            final String faqHref = item.attr("href").split("#")[0];
            final Path faqItemFile = faq.getParent().resolve("faq").resolve(faqHref);
            if (!Files.exists(faqItemFile)) {
                call.accept(Severity.ERROR, "[FAQ] A file '" + faqHref + "' is linked in faq list but does not exist");
            } else {
                foundFaqs.put(faqHref + "@" + faq.getFileName(), item.text());
                foundLinkedFaqs.put(FAQ_DEFAULT_PAGE_PREFIX + faqHref.split("#")[0],
                    item.text().trim());
            }
        });
    }

    private static void validatePdf(final Path pdfToc, final BiConsumer<Severity, String> call)
        throws IOException, SAXException {
        final String pdfName = pdfToc.getFileName().toString();
        final org.jsoup.nodes.Document pdfDoc = Jsoup.parse(readAll(pdfToc, ENCODING_UTF8));
        //check a title has been defined
        final Element title = pdfDoc.getElementsByTag("title").first();
        if (title != null) {
            if (title.text().isEmpty()) {
                call.accept(Severity.ERROR, "[PDF] [" + pdfName + "] Title cannot be empty");
            }
        } else {
            call.accept(Severity.ERROR, "[PDF] [" + pdfName + "] No valid title was found");
        }

        //checking bare pdf's toc structure
        Elements nav = pdfDoc.getElementsByTag("nav");
        checkNavStructure(nav, "[PDF] [" + pdfName + "]", call);

        //check linked pages actually do exist
        final Path pagesPathForLang = pdfToc.getParent().getParent().resolve(CONFIG_NAMES_FOLDER_PAGES);
        final Path mainDocsFolder = pdfToc.getParent().getParent().getParent();
        Elements links = pdfDoc.getElementsByTag("a");
        links.stream().forEach(link -> {
            final String[] linkTokens = link.attr("href").split("/");
            final String pageLink = linkTokens[linkTokens.length - 1];
            boolean pageExists = false;
            final Path pagesCheckFolder;
            final String refLanguage = link.attr(DOCET_HTML_ATTR_REFERENCE_LANGUAGE_NAME);
            if (refLanguage.isEmpty()) {
                pagesCheckFolder = pagesPathForLang;
            } else {
                final Language validlang = Language.getLanguageByCode(refLanguage);
                if (validlang == null) {
                    call.accept(Severity.ERROR, "[PDF] [" + pdfName + "] page '" + pageLink + "' reference language '"
                        + refLanguage + "' is not supported!");
                    return;
                }
                pagesCheckFolder = mainDocsFolder.resolve(refLanguage);
            }
            try {
                pageExists = fileExists(pagesCheckFolder, pageLink);
                if (!pageExists) {
                    final String referenceLangMsg;
                    if (refLanguage.isEmpty()) {
                        referenceLangMsg = "";
                    } else {
                        referenceLangMsg = " (reference language=" + refLanguage + ")";
                    }
                    call.accept(Severity.ERROR, "[PDF] [" + pdfName + "] page '" + pageLink + "'"
                        + referenceLangMsg + " does not exist");
                }
            } catch (IOException e) {
                call.accept(Severity.ERROR, "[PDF] [" + pdfName + "] page '" + pageLink + "' existence cannot be checked. Reason: " + e);
            }
        });
    }

    private static void validateToc(final Path toc, final Map<String, String> linkedPagesFound, BiConsumer<Severity, String> call)
        throws IOException, SAXException {
        final org.jsoup.nodes.Document htmlDoc = Jsoup.parseBodyFragment(readAll(toc, ENCODING_UTF8));

        // check structure
        Elements nav = htmlDoc.getElementsByTag("nav");
        checkNavStructure(nav, "[TOC]", call);

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
                    if (isFaq) {
                        linkedPagesFound.put(FAQ_DEFAULT_PAGE_PREFIX + pageLink.split("#")[0], link.text().trim());
                    } else {
                        linkedPagesFound.put(pageLink.split("#")[0], link.text().trim());
                    }
                }
                if (pageAlreadyLinked) {
                    call.accept(Severity.ERROR, "[TOC] " + (isFaq ? "FAQ" : "") + " page '" + pageLink + "' is mentioned in TOC multiple times");
                }
            } catch (IOException e) {
                call.accept(Severity.ERROR, "[TOC] Referred " + (isFaq ? "FAQ" : "") + " page '" + pageLink + "' existence cannot be checked. Reason: " + e);
            }
        });
    }

    private static void checkNavStructure(final Elements nav, final String logPrefix, BiConsumer<Severity, String> call) {
        final int navNum = nav.size();
        if (navNum == 0) {
            call.accept(Severity.ERROR, logPrefix + " is currently empty");
        }
        if (navNum > 1) {
            call.accept(Severity.ERROR, logPrefix + " defined " + navNum + " navs. One expected.");
        }
        nav.stream().forEach(n -> {
            final Elements ul = n.getElementsByTag("ul");
            final int ulNum = ul.size();
            if (ulNum == 0) {
                call.accept(Severity.ERROR, logPrefix + " is currently empty");
            }
            final List<Node> straightChildUls = n.childNodes().stream().filter(nd -> "ul".equals(nd.nodeName()))
                .collect(Collectors.toList());
            if (straightChildUls.size() > 1) {
                call.accept(Severity.ERROR, logPrefix + " nav contains " + ulNum + " ULs. One expected.");
            }
            if (ulNum == 1) {
                final Elements lis = ul.get(0).children();
                if (lis.isEmpty()) {
                    call.accept(Severity.ERROR, logPrefix + " is currently empty");
                }
                lis.stream().forEach(l -> {
                    if (!"li".equals(l.tag().toString())) {
                        call.accept(Severity.ERROR, logPrefix + " Expected <li>, found: <" + l.tag() + ">");
                    } else {
                        final Elements anchor = l.children();
                        switch (anchor.size()) {
                            case 0:
                                call.accept(Severity.ERROR, logPrefix + " Empty <li> found");
                                break;
                            case 1:
                                if (!"a".equals(anchor.get(0).tag().toString())) {
                                    call.accept(Severity.ERROR, logPrefix + " found <li> with no <a> included");
                                }
                                break;
                            case 2:
                                if (!"a".equals(anchor.get(0).tag().toString())) {
                                    call.accept(Severity.ERROR, logPrefix + " <li> -> expected <a>, found <" + anchor.get(0).tag() + ">");
                                }
                                if (!"ul".equals(anchor.get(1).tag().toString())) {
                                    call.accept(Severity.ERROR, logPrefix + " <li> -> expected <ul>, found <" + anchor.get(1).tag() + ">");
                                }
                                break;
                            default:
                                call.accept(Severity.ERROR, logPrefix + " found <li> containing " + anchor.size() + " elements."
                                    + " An <li> must include no more than a <a> and a <ul>!");
                        }
                    }
                });
            }
        });
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
        if (Files.isDirectory(langPath)) {
            final Path tocPath = langPath.getParent().resolve(CONFIG_NAMES_FILE_TOC);
            final Path outputDir = outDir;
            TOC comprenhesiveToc;
            try {
                comprenhesiveToc = parseTOCFromPath(tocPath, tmpDir, messages);
                comprenhesiveToc.getItems().stream().forEach(item -> {
                    String outFileName = Paths.get(item.getPagePath()).getFileName().toString();
                    outFileName = outFileName.replaceFirst("\\.html", "\\.pdf");
                    final Path pdfFile = outputDir.resolve(outFileName);
                    final List<TOC.TOCItem> docToc = new ArrayList<>();
                    docToc.add(item);
                    try {
                        log.debug("reading " + item + "; generating pdf: " + pdfFile);
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
        final org.jsoup.nodes.Document htmlDoc = Jsoup.parseBodyFragment(readAll(tocFilePath, ENCODING_UTF8));
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
        return toc;
    }

    private static Path saveDocumentToDirectory(final org.jsoup.nodes.Document doc, final String fileName, final Path tmpDir) throws IOException {
        final Path outTmpPath = tmpDir.resolve(fileName);
        writeAll(outTmpPath, doc.outerHtml(), ENCODING_UTF8);
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
        final org.jsoup.nodes.Document doc = Jsoup.parse(readAll(htmlPagePath, ENCODING_UTF8), "", Parser.xmlParser());
        doc.getElementsByTag("head").append("<link href=\"src/docs/mndoc.css\" type=\"text/css\" rel=\"stylesheet\" />");
        final Elements images = doc.select("img");
        for (final Element img : images) {
            img.attr("src", searchFileInBasePathByName(basePath, img.attr("src")).toString());
        }
        return doc;
    }

    private static Path searchFileInBasePathByName(final Path basePath, final String fileName) throws IOException {
        final Holder<Path> result = new Holder<>(null);
        final String parsedFilename = fileName.split("#")[0];
        Files.walkFileTree(basePath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                if (file.endsWith(parsedFilename)) {
                    result.setValue(file);
                    return FileVisitResult.TERMINATE;
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return result.value;
    }

    public static int zippingDocs(final Path srcDir, final Path outDir, final Path indexDir, final boolean includeIndex, final Path zipFileName,
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
        final String languagePathPattern = "(it|en|fr)" + pathRegexSeparator + "(faq|imgs|pdf|pages|toc\\.html)";
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

        final org.jsoup.nodes.Document htmlDoc = Jsoup.parseBodyFragment(readAll(tocFile, ENCODING_UTF8));
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
        writeAll(outTocFile, htmlDoc.outerHtml(), ENCODING_UTF8);
        return outTocFile;

    }

    private static void writeFileToArchive(final ZipOutputStream zos, final Path baseSrcPath, final Path filePath) throws IOException {
        final byte[] buffer = new byte[1024];
        final String zipPath = baseSrcPath.resolve(extractLanguageRelativePath(filePath)).toString();
        final ZipEntry ze = new ZipEntry(zipPath);
        zos.putNextEntry(ze);
        try (InputStream in = Files.newInputStream(filePath);) {
            int len;
            while ((len = in.read(buffer)) > 0) {
                zos.write(buffer, 0, len);
            }
        }
        zos.closeEntry();
    }

    private static String extractLanguageRelativePath(final Path absolutePath) {
        final String fileName;
        if (absolutePath != null && absolutePath.getFileName() != null) {
            fileName = absolutePath.getFileName().toString();
        } else {
            fileName = "";
        }
        final String pathRegexSeparator;
        if ("\\".equals(File.separator)) {
            pathRegexSeparator = "\\\\";
        } else {
            pathRegexSeparator = File.separator;
        }
        final String languagePathPattern = "(it|en|fr)" + pathRegexSeparator + "(faq|imgs|pages|pdf|toc\\.html)";
        Pattern pattern = Pattern.compile(languagePathPattern);
        final String pathString;
        if (absolutePath != null) {
            pathString = absolutePath.toString();
        } else {
            pathString = "";
        }
        Matcher matcher = pattern.matcher(pathString);
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

    public static void indexDocs(final Path outDir, final Path srcDir, final Map<Language, List<FaqEntry>> faqs,
        final Log log, final boolean compact)
        throws MojoFailureException {
        for (Language lang : Language.values()) {
            Path langPath = srcDir.resolve(lang.toString());
            langPath = langPath.resolve(CONFIG_NAMES_FOLDER_PAGES);
            if (Files.isDirectory(langPath)) {
                indexDocsForLanguage(outDir, langPath, lang, faqs.get(lang), log, compact);
            } else {
                log.warn("[" + lang + "] No folder found for language");
            }
        }
    }

    public static int indexDocsForLanguage(final Path outDir, final Path path, final Language lang,
        final List<FaqEntry> faqs, final Log log, final boolean compact)
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

                if (compact) {
                    writer.forceMerge(1);
                }
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
            final org.jsoup.nodes.Document htmlDoc = Jsoup.parseBodyFragment(readAll(file, ENCODING_UTF8));
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
        final org.jsoup.nodes.Document htmlDoc = Jsoup.parseBodyFragment(readAll(file, ENCODING_UTF8));

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

    private static String readAll(Path path, Charset cs) throws IOException {
        return new String(Files.readAllBytes(path), cs);
    }

    private static void writeAll(Path path, String data, Charset cs) throws IOException {
        try(OutputStream os = Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            os.write(data.getBytes(ENCODING_UTF8));
        }
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
