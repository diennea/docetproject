package docet.engine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;

import com.itextpdf.text.Chunk;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.Image;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.ColumnText;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfCopy;
import com.itextpdf.text.pdf.PdfCopy.PageStamp;
import com.itextpdf.text.pdf.PdfImportedPage;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfSmartCopy;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.pdf.draw.DottedLineSeparator;

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
            final Map<String, PdfReader> docsToMerge = new TreeMap<>();
            final List<PdfImportedPage> pagesToPrint = new ArrayList<>();
            final Map<String, String> idByTitles = new HashMap<>();
            final Map<Integer, String> toc = new TreeMap<>();
            final Document pdfDoc = new Document();
            pdfDoc.setMargins(30, 30, 50, 50);
            final PdfSmartCopy copy = new PdfSmartCopy(pdfDoc, out);
            copy.setViewerPreferences(PdfWriter.PageModeUseOutlines);
            pdfDoc.addTitle(doc.getTitle());
            pdfDoc.open();
            this.createCoverPage(doc.getPackageName(), doc.getTitle(), doc.getLang(), copy, ctx, docsToMerge);
            int summaryIndex = 1;
            for (final SummaryEntry entry: doc.getSummary()) {
                this.parseSummaryForEntry(entry, doc.getPackageName(), copy, ctx, docsToMerge, "" + summaryIndex, idByTitles);
                summaryIndex++;
            }
            int n;
            int pageNo = 0;
            PdfImportedPage page;
            for (final Entry<String, PdfReader> reader: docsToMerge.entrySet()) {
                n = reader.getValue().getNumberOfPages();
                if (!reader.getKey().matches("\\d\\.\\d\\.\\d.*") && !reader.getKey().matches("\\d\\.\\d.*")) {
                    toc.put(pageNo + 1, reader.getKey());
                }
                for (int i = 0; i < n; ) {
                    pageNo++;
                    page = copy.getImportedPage(reader.getValue(), ++i, false);
                    pagesToPrint.add(page);
                }
            }

            final PdfReader tocReader = this.createTOC(toc, copy, ctx, idByTitles);
            for (int i = 0; i < tocReader.getNumberOfPages();) {
                pagesToPrint.add(i + 1, copy.getImportedPage(tocReader, ++i, false));
            }
            PageStamp stamp;
            Chunk chunk;
            int i = 0;
            final BaseFont bf = BaseFont.createFont();
            final Font font = new Font(bf, 10);
            font.setColor(68, 68, 68);
            for (final PdfImportedPage p: pagesToPrint) {
                if (i > 0) {
                    stamp = copy.createPageStamp(p);
                    chunk = new Chunk(String.format("%d", i), font);
                    if (i == 1) {
                        chunk.setLocalDestination("p" + pageNo);
                    }
                    ColumnText.showTextAligned(stamp.getUnderContent(),
                            Element.ALIGN_RIGHT, new Phrase(chunk),
                            559, 810, 0);
                    stamp.alterContents();
                }
                copy.addPage(p);
                i++;
            }
            pdfDoc.close();

            tocReader.close();
            for (final PdfReader reader: docsToMerge.values()) {
                reader.close();
            }
        } catch (DocumentException | IOException | DocetException ex) {
            throw new DocetDocumentParsingException("Impossible to generate pdf", ex);
        }
    }

    private PdfReader createTOC(final Map<Integer, String> toc, final PdfCopy copy, final DocetExecutionContext ctx,
        final Map<String, String> idFromTitles)
        throws DocetDocumentParsingException, IOException, DocumentException, DocetException {
        final PdfReader reader;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();) {
            Document document = new Document(PageSize.A4);
            document.setMargins(60, 60, 100, 70);

            PdfWriter pdfWriter = PdfWriter.getInstance(document, baos);
            pdfWriter.setViewerPreferences(PdfWriter.PageModeUseOutlines);
            pdfWriter.setPageEvent(((PdfDocetDocumentParser)this.pdfParser).getFooterHelper());
            document.open();
            final BaseFont bf = BaseFont.createFont();
            final Font font = new Font(bf, 12);
            font.setColor(68, 68, 68);
            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(100);
            table.setWidths(new int[]{6, 4});
            table.setHeaderRows(0);
            for (final Entry<Integer, String> entry: toc.entrySet()) {
                if (!"0".equals(entry.getValue())) {
                    final String title = entry.getValue();
                    final String page = entry.getKey() + "";

                    Paragraph p = new Paragraph(title, font);
                    p.add(new Chunk(new DottedLineSeparator()));
                    PdfPCell cell = new PdfPCell(p);
                    cell.setBorder(Rectangle.NO_BORDER);
                    cell.setFixedHeight(20);
                    table.addCell(cell);
                    p = new Paragraph(new Chunk(new DottedLineSeparator()));
                    p.setFont(font);
                    p.add(page);
                    cell = new PdfPCell(p);
                    cell.setBorder(Rectangle.NO_BORDER);
                    cell.setFixedHeight(20);
                    cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                    table.addCell(cell);
                }
            }
            document.add(table);
            document.close();
            reader = new PdfReader(baos.toByteArray());

        } catch (IOException | DocumentException e) {
            throw new DocetDocumentParsingException("Impossible to generate pdf", e);
        }
        return reader;

    }

    private void createCoverPage(final String packageName, final String title, final String lang, final PdfCopy copy,
        final DocetExecutionContext ctx, final Map<String, PdfReader> docsToMerge)
        throws DocetDocumentParsingException, IOException, DocumentException, DocetException {
//        final String htmlCover = Jsoup.parse("<div class=\"cover\" id=\"main\"><h1>" + title + "</h1><img class=\"coverimage\" src=\"data:image/png;base64,"
//            +java.util.Base64.getEncoder().encodeToString( this.manager.getIconForPdfsCover(ctx)) + "\" />"
//            + "</div>", "", Parser.xmlParser())
//            .toString();
        final PdfReader reader;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();) {
            Document document = new Document(PageSize.A4);
            document.setMargins(60, 60, 100, 70);

            PdfWriter pdfWriter = PdfWriter.getInstance(document, baos);
            pdfWriter.setViewerPreferences(PdfWriter.PageModeUseOutlines);
            pdfWriter.setPageEvent(((PdfDocetDocumentParser)this.pdfParser).getFooterHelper());
            document.open();
            final Image icon = Image.getInstance(this.manager.getIconForPdfsCover(ctx));
            icon.scaleToFit(256, 256);
//            icon.setTop(0);
//            icon.setLeft(0);
            document.add(icon);
            Paragraph p = new Paragraph(new Chunk(new DottedLineSeparator()));
            document.add(p);
            final BaseFont bf = BaseFont.createFont();
            final Font font = new Font(bf, 20);
            font.setColor(68, 68, 68);
            PdfContentByte cb = pdfWriter.getDirectContent();
            cb.saveState();
            cb.beginText();
            cb.moveText(80, 400);
            cb.setFontAndSize(bf, 20);
            cb.showText(title);
            cb.endText();
            cb.restoreState();
            document.close();
            reader = new PdfReader(baos.toByteArray());

        } catch (IOException | DocumentException e) {
            throw new DocetDocumentParsingException("Impossible to generate pdf", e);
        }
        docsToMerge.put("0", reader);
    }

    private void parseSummaryForEntry(final SummaryEntry entry, final String packageName, final PdfCopy copy,
        final DocetExecutionContext ctx, final Map<String, PdfReader> docsToMerge, final String summaryIndex, final Map<String, String> idByTitles)
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
        rawHtml.select("#main").get(0).before("<a name=\"" + id + "\"></a>");
        final String title = summaryIndex + " " + entry.getName();
        idByTitles.put(title, id);
        final PdfReader reader = new PdfReader(pdfParser.parsePage(rawHtml.toString()));
        docsToMerge.put(title, reader);

//        copy.addDocument(reader);
//        reader.close();
        int subindex = 1;
        String subsummaryIndex = summaryIndex + "." + subindex;
        for (final SummaryEntry subEntry: entry.getSubSummary()) {
            this.parseSummaryForEntry(subEntry, packageName, copy, ctx, docsToMerge, subsummaryIndex, idByTitles);
            subindex++;
            subsummaryIndex = summaryIndex + "." + subindex;
        }
    }
}