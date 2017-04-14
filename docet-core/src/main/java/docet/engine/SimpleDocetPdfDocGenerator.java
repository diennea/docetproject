package docet.engine;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;

import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.pdf.PdfCopy;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfWriter;

import docet.DocetDocumentGenerator;
import docet.DocetExecutionContext;
import docet.error.DocetDocumentParsingException;
import docet.error.DocetException;
import docet.model.DocetDocument;
import docet.model.SummaryEntry;

public class SimpleDocetPdfDocGenerator implements DocetDocumentGenerator {
    
    private final DocetDocumentParser pdfParser;
    private final DocetManager manager;
    
    public SimpleDocetPdfDocGenerator(final DocetDocumentParser pdfParser, final DocetManager manager) {
        this.pdfParser = pdfParser;
        this.manager = manager;
    }

    @Override
    public void generateDocetDocument(final DocetDocument doc, final DocetExecutionContext ctx, final OutputStream out)
        throws DocetDocumentParsingException {
        try {
            final Document pdfDoc = new Document();
            pdfDoc.setMargins(30, 30, 50, 50);
            final PdfCopy copy = new PdfCopy(pdfDoc, out);
            copy.setViewerPreferences(PdfWriter.PageModeUseOutlines);
            pdfDoc.addTitle(doc.getTitle());
            pdfDoc.open();
            this.createCoverPage(doc.getPackageName(), doc.getTitle(), doc.getLang(), copy, ctx);
            this.createTOC(doc.getSummary(), copy, ctx);
            for (final SummaryEntry entry: doc.getSummary()) {
                this.parseSummaryForEntry(entry, doc.getPackageName(), copy, ctx);
            }
            pdfDoc.close();
        } catch (DocumentException | IOException | DocetException ex) {
            throw new DocetDocumentParsingException("Impossible to generate pdf", ex);
        }
    }

    private void createTOC(final List<SummaryEntry> tocs, final PdfCopy copy, final DocetExecutionContext ctx)
        throws DocetDocumentParsingException, IOException, DocumentException, DocetException {
        final org.jsoup.nodes.Document htmlToc = Jsoup.parse("<div class=\"cover\" id=\"main\">"
            + "</div>", "", Parser.xmlParser());
        final Element divMain = htmlToc.select("#main").get(0);
        for (final SummaryEntry entry: tocs) {
            divMain.append("<p><a href=\"#" + entry.getTargetPageId() + "\">" + entry.getName() + "</a></p>");
        }
        final PdfReader reader = new PdfReader(pdfParser.parsePage(htmlToc.toString()));
        copy.addDocument(reader);
        reader.close();
    }

    private void createCoverPage(final String packageName, final String title, final String lang, final PdfCopy copy, final DocetExecutionContext ctx)
        throws DocetDocumentParsingException, IOException, DocumentException, DocetException {
        final String htmlCover = Jsoup.parse("<div class=\"cover\" id=\"main\"><h1>" + title + "</h1><img class=\"coverimage\" src=\"data:image/png;base64,"
            +java.util.Base64.getEncoder().encodeToString( this.manager.getIconForPackage(packageName, ctx)) + "\" />"
            + "</div>", "", Parser.xmlParser())
            .toString();
        final PdfReader reader = new PdfReader(pdfParser.parsePage(htmlCover));
        copy.addDocument(reader);
        reader.close();
    }

    private void parseSummaryForEntry(final SummaryEntry entry, final String packageName, final PdfCopy copy,
        final DocetExecutionContext ctx)
            throws IOException, DocetDocumentParsingException, DocetException, DocumentException {
        final String id = entry.getTargetPageId();
        final String lang = entry.getLang();
        final org.jsoup.nodes.Document rawHtml = Jsoup.parse(this.manager.servePageIdForLanguageForPackage(packageName, id, lang,
            DocetDocFormat.TYPE_PDF, false, null, ctx), "", Parser.xmlParser());
        final int level = entry.getLevel();
        for (int i = 3; i >= 1; i--) {
            final int currentLevel = i;
            rawHtml.select("h" + i).forEach(e -> e.tagName("h" + (currentLevel + level)));
        }
        rawHtml.select("#main").get(0).before("<a name=\"" + id + "\">Ciao</a>");
        final PdfReader reader = new PdfReader(pdfParser.parsePage(rawHtml.toString()));
        copy.addDocument(reader);
        reader.close();
        for (final SummaryEntry subEntry: entry.getSubSummary()) {
            this.parseSummaryForEntry(subEntry, packageName, copy, ctx);
        }
    }
}