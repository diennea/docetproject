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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;

import com.itextpdf.text.BaseColor;
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
import com.itextpdf.text.pdf.PdfPageEventHelper;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfSmartCopy;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.pdf.draw.DottedLineSeparator;

import docet.DocetDocumentGenerator;
import docet.DocetDocumentPlaceholder;
import docet.DocetDocumentResourcesAccessor;
import docet.DocetExecutionContext;
import docet.DocetLanguage;
import docet.DocetUtils;
import docet.error.DocetDocumentParsingException;
import docet.error.DocetException;
import docet.model.DocetDocument;
import docet.model.SummaryEntry;

public class SimpleDocetPdfDocGenerator implements DocetDocumentGenerator {

    private static final String COVERPAGE_PAGE_ID = "0";
    private static final String DEFAULT_HEADER_PAGE_BACKGROUND_COLOR = "#0C2939";
    private static final String DEFAULT_HEADER_PAGE_FONT_SIZE = "8";
    private static final String DEFAULT_HEADER_PAGE_TEXT_COLOR = "#FFFFFF";
    private static final String DEFAULT_FOOTER_PAGE_BACKGROUND_COLOR = "#DDD9D9";
    private static final String DEFAULT_FOOTER_PAGE_FONT_SIZE = "8";
    private static final String DEFAULT_FOOTER_PAGE_TEXT_COLOR = "#6B7880";
    private static final int DEFAULT_FOOTER_BORDER_WIDTH = 30;
    private static final String DEFAULT_FOOTER = "Docet - &copy;&nbsp;Copyright 2017";

    private final DocetDocumentParser pdfParser;
    private final DocetManager manager;
    private final PdfPageEventHelper footerHelper;
    private final EnumMap<DocetDocumentPlaceholder, String> placeholders;
    
    public SimpleDocetPdfDocGenerator(final DocetDocumentParser pdfParser, final DocetManager manager)
        throws IOException {
        this.pdfParser = pdfParser;
        this.manager = manager;
        this.placeholders = new EnumMap<>(DocetDocumentPlaceholder.class);
        this.footerHelper = new PdfFooterHandler(DEFAULT_FOOTER, DEFAULT_FOOTER);
    }

    private void loadPlaceHolders(final DocetLanguage docetLang, final DocetDocumentResourcesAccessor placeholderAccessor) {
      //footer for cover page
        placeholders.put(DocetDocumentPlaceholder.PDF_FOOTER_COVER, 
            placeholderAccessor.getPlaceholderForDocument(DocetDocumentPlaceholder.PDF_FOOTER_COVER, docetLang));
        placeholders.put(DocetDocumentPlaceholder.PDF_FOOTER_COVER_BACKGROUND_COLOR,
            placeholderAccessor.getPlaceholderForDocument(DocetDocumentPlaceholder.PDF_FOOTER_COVER_BACKGROUND_COLOR, docetLang));
        placeholders.put(DocetDocumentPlaceholder.PDF_FOOTER_COVER_TEXT_COLOR,
            placeholderAccessor.getPlaceholderForDocument(DocetDocumentPlaceholder.PDF_FOOTER_COVER_TEXT_COLOR, docetLang));
        placeholders.put(DocetDocumentPlaceholder.PDF_FOOTER_COVER_FONT_SIZE,
            placeholderAccessor.getPlaceholderForDocument(DocetDocumentPlaceholder.PDF_FOOTER_COVER_FONT_SIZE, docetLang));

        placeholders.put(DocetDocumentPlaceholder.PDF_FOOTER_PAGE_BACKGROUND_COLOR,
            placeholderAccessor.getPlaceholderForDocument(DocetDocumentPlaceholder.PDF_FOOTER_PAGE_BACKGROUND_COLOR, docetLang));
        placeholders.put(DocetDocumentPlaceholder.PDF_FOOTER_PAGE_TEXT_COLOR,
            placeholderAccessor.getPlaceholderForDocument(DocetDocumentPlaceholder.PDF_FOOTER_PAGE_TEXT_COLOR, docetLang));
        placeholders.put(DocetDocumentPlaceholder.PDF_FOOTER_PAGE_FONT_SIZE,
            placeholderAccessor.getPlaceholderForDocument(DocetDocumentPlaceholder.PDF_FOOTER_PAGE_FONT_SIZE, docetLang));

        placeholders.put(DocetDocumentPlaceholder.PDF_HEADER_PAGE_BACKGROUND_COLOR,
            placeholderAccessor.getPlaceholderForDocument(DocetDocumentPlaceholder.PDF_HEADER_PAGE_BACKGROUND_COLOR, docetLang));
        placeholders.put(DocetDocumentPlaceholder.PDF_HEADER_PAGE_TEXT_COLOR,
            placeholderAccessor.getPlaceholderForDocument(DocetDocumentPlaceholder.PDF_HEADER_PAGE_TEXT_COLOR, docetLang));
        placeholders.put(DocetDocumentPlaceholder.PDF_HEADER_PAGE_FONT_SIZE,
            placeholderAccessor.getPlaceholderForDocument(DocetDocumentPlaceholder.PDF_HEADER_PAGE_FONT_SIZE, docetLang));
    }

    private void updateFooter() throws DocetDocumentParsingException {
        try {
            ((PdfFooterHandler) this.footerHelper).updateFooter(
                this.placeholders.get(DocetDocumentPlaceholder.PDF_FOOTER_COVER),
                this.placeholders.get(DocetDocumentPlaceholder.PDF_FOOTER_COVER_TEXT_COLOR),
                this.placeholders.get(DocetDocumentPlaceholder.PDF_FOOTER_COVER_BACKGROUND_COLOR),
                this.placeholders.get(DocetDocumentPlaceholder.PDF_FOOTER_COVER_FONT_SIZE));
            
        } catch (IOException ex) {
            throw new DocetDocumentParsingException(ex);
        }
    }

    @Override
    public void generateDocetDocument(final DocetDocument doc, final DocetExecutionContext ctx, final OutputStream out,
        final DocetDocumentResourcesAccessor placeholderAccessor)
        throws DocetDocumentParsingException {
        try {
            final DocetLanguage docetLang = DocetLanguage.parseDocetLanguageByName(doc.getLang());
            loadPlaceHolders(docetLang, placeholderAccessor);
            updateFooter();
            
            //footer for regular pages
            String pageFooter = placeholderAccessor.getPlaceholderForDocument(DocetDocumentPlaceholder.PDF_FOOTER_PAGE, docetLang);
            if (pageFooter == null || pageFooter.isEmpty()) {
                pageFooter = doc.getProductName() + " " + doc.getProductVersion() + " - " + doc.getTitle();
            }
            final Map<String, PdfReader> docsToMerge = new HashMap<>();
            final List<String> docsTitleList = new ArrayList<>();
            final List<PdfImportedPage> pagesToPrint = new ArrayList<>();
            final Map<String, String> idByTitles = new HashMap<>();
            final Map<Integer, String> toc = new TreeMap<>();
            final Document pdfDoc = new Document();
            pdfDoc.setMargins(30, 30, 50, 50);
            final PdfSmartCopy copy = new PdfSmartCopy(pdfDoc, out);
            copy.setViewerPreferences(PdfWriter.PageModeUseOutlines);
            pdfDoc.addTitle(doc.getTitle());
            pdfDoc.open();
            this.createCoverPage(doc.getPackageName(), doc.getTitle(), doc.getProductName(), doc.getProductVersion(), docetLang, copy, ctx, 
                placeholderAccessor, docsToMerge, docsTitleList);
            int summaryIndex = 1;
            for (final SummaryEntry entry: doc.getSummary()) {
                this.parseSummaryForEntry(entry, doc.getPackageName(), copy, ctx, docsToMerge, "" + summaryIndex, docsTitleList);
                summaryIndex++;
            }
            int n;
            int pageNo = 0;
            PdfImportedPage page;
            for (final String title: docsTitleList) {
                PdfReader reader = docsToMerge.get(title);
                n = reader.getNumberOfPages();
                if (!title.startsWith("###")) {
                    toc.put(pageNo + 1, title);
                }
                for (int i = 0; i < n; ) {
                    pageNo++;
                    page = copy.getImportedPage(reader, ++i, false);
                    pagesToPrint.add(page);
                }
            }

            final PdfReader tocReader = this.createTOC(toc, copy, ctx, idByTitles);
            final int tocNumPages = tocReader.getNumberOfPages();
            for (int i = 0; i < tocReader.getNumberOfPages();) {
                pagesToPrint.add(i + 1, copy.getImportedPage(tocReader, ++i, false));
            }
            PageStamp stamp;
            Chunk chunk;
            int i = 0;
            final BaseFont bf = BaseFont.createFont();
            String fontSize = placeholders.getOrDefault(DocetDocumentPlaceholder.PDF_FOOTER_PAGE_FONT_SIZE,
                DEFAULT_FOOTER_PAGE_FONT_SIZE);
            if (fontSize.isEmpty()) {
                fontSize = DEFAULT_FOOTER_PAGE_FONT_SIZE;
            }
            int computedFontSize;
            try {
                computedFontSize = Integer.parseInt(fontSize);
            } catch(NumberFormatException ex) {
                computedFontSize = Integer.parseInt(DEFAULT_FOOTER_PAGE_FONT_SIZE);
            }
            final Font fontFooter = new Font(bf, computedFontSize);
            try {
                fontSize = placeholders.getOrDefault(DocetDocumentPlaceholder.PDF_HEADER_PAGE_FONT_SIZE, 
                    DEFAULT_HEADER_PAGE_FONT_SIZE);
                if (fontSize.isEmpty()) {
                    fontSize = DEFAULT_HEADER_PAGE_FONT_SIZE;
                }
                computedFontSize = Integer.parseInt(fontSize);
            } catch(NumberFormatException ex) {
                computedFontSize = Integer.parseInt(DEFAULT_HEADER_PAGE_FONT_SIZE);
            }
            final Font fontHeader = new Font(bf, computedFontSize);
            String footerColor = placeholders.getOrDefault(DocetDocumentPlaceholder.PDF_FOOTER_PAGE_TEXT_COLOR,
                DEFAULT_FOOTER_PAGE_TEXT_COLOR);
            if (footerColor.isEmpty()) {
                footerColor = DEFAULT_FOOTER_PAGE_TEXT_COLOR;
            }
            final Integer[] rgbFooter = DocetUtils.convertHexColorToRgb(footerColor);
            fontFooter.setColor(rgbFooter[0], rgbFooter[1], rgbFooter[2]);
            String headerColor = placeholders.getOrDefault(DocetDocumentPlaceholder.PDF_HEADER_PAGE_TEXT_COLOR,
                DEFAULT_HEADER_PAGE_TEXT_COLOR);
            if (headerColor.isEmpty()) {
                headerColor = DEFAULT_HEADER_PAGE_TEXT_COLOR;
            }
            final Integer[] rgbHeader = DocetUtils.convertHexColorToRgb(headerColor);
            fontFooter.setColor(rgbFooter[0], rgbFooter[1], rgbFooter[2]);
            fontHeader.setColor(rgbHeader[0], rgbHeader[1], rgbHeader[2]);
            headerColor = placeholders.getOrDefault(DocetDocumentPlaceholder.PDF_HEADER_PAGE_BACKGROUND_COLOR,
                DEFAULT_HEADER_PAGE_BACKGROUND_COLOR);
            if (headerColor.isEmpty()) {
                headerColor = DEFAULT_HEADER_PAGE_BACKGROUND_COLOR;
            }
            final Integer[] rgbHeaderBck = DocetUtils.convertHexColorToRgb(headerColor);
            String footerBckColor = placeholders.getOrDefault(DocetDocumentPlaceholder.PDF_FOOTER_PAGE_BACKGROUND_COLOR,
                DEFAULT_FOOTER_PAGE_BACKGROUND_COLOR);
            if (footerBckColor.isEmpty()) {
                footerBckColor = DEFAULT_FOOTER_PAGE_BACKGROUND_COLOR;
            }
            final Integer[] rgbFooterBck = DocetUtils.convertHexColorToRgb(footerBckColor);
            for (final PdfImportedPage p: pagesToPrint) {
                if (i > 0) {
                    final String footerText;
                    final String headerText;
                    final int align;
                    final float x;
                    final int actualPageNo = i - tocNumPages + 1;
                    final String pageNumTxt;
                    if (i > tocNumPages) {
                        pageNumTxt = actualPageNo + "";
                    } else {
                        pageNumTxt = "";
                    }
                    if (i % 2 == 0) {
                        footerText = pageNumTxt;
                        align = Element.ALIGN_LEFT;
                        headerText = doc.getProductName();
                        x = DEFAULT_FOOTER_BORDER_WIDTH;
                    } else {
                        footerText = pageFooter + (pageNumTxt.length() == 0 ? "" : " | " + pageNumTxt);
                        align = Element.ALIGN_RIGHT;
                        headerText = doc.getProductName();
                        x = copy.getPageSize().getWidth() - DEFAULT_FOOTER_BORDER_WIDTH;
                    }
                    stamp = copy.createPageStamp(p);
                    chunk = new Chunk(String.format(footerText, i), fontFooter);
                    if (i == 1) {
                        chunk.setLocalDestination("p" + pageNo);
                    }
                    ColumnText.showTextAligned(stamp.getOverContent(),
                        align, new Phrase(chunk), x, 20, 0);
                    Rectangle footerBck = new Rectangle(copy.getPageSize().getWidth(), 0, 0, 45);
                    footerBck.setBackgroundColor(new BaseColor(rgbFooterBck[0], rgbFooterBck[1], rgbFooterBck[2]));
//                    footerBck.setBorder(Rectangle.TOP);
                    footerBck.setBorderWidth(0.01f);
//                    footerBck.setBorderColor(BaseColor.DARK_GRAY);
                    stamp.getUnderContent().rectangle(footerBck);
                    chunk = new Chunk(headerText, fontHeader);
                    ColumnText.showTextAligned(stamp.getOverContent(),
                        align, new Phrase(chunk), x, copy.getPageSize().getHeight() - 25, 0);
                    Rectangle headerBck = new Rectangle(copy.getPageSize().getWidth(), 
                        copy.getPageSize().getHeight() - 40, 0, copy.getPageSize().getHeight());
                    headerBck.setBackgroundColor(new BaseColor(rgbHeaderBck[0], rgbHeaderBck[1], rgbHeaderBck[2]));
                    stamp.getUnderContent().rectangle(headerBck);
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

    private void createCoverPage(final String packageName, final String title, final String productName, final String productVersion, 
        final DocetLanguage docetLang, final PdfCopy copy, final DocetExecutionContext ctx,
        final DocetDocumentResourcesAccessor placeholderAccessor, final Map<String, PdfReader> docsToMerge, final List<String> docTitles)
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
            pdfWriter.setPageEvent(this.footerHelper);
            document.open();
            final byte[] rawImage = placeholderAccessor.getImageForCovers();
            if (rawImage.length > 0) {
                final Image icon = Image.getInstance(rawImage);
                icon.scaleToFit(document.getPageSize().getWidth(), document.getPageSize().getHeight());
                icon.setAbsolutePosition(0, 0);
                pdfWriter.getDirectContent().addImage(icon, false);
            } else {
                final Rectangle cover = new Rectangle(document.getPageSize().getWidth(), 0, 0,
                    document.getPageSize().getHeight());
                cover.setBackgroundColor(BaseColor.DARK_GRAY);
                pdfWriter.getDirectContent().rectangle(cover);
            }
            final BaseFont bfTitle = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, true);
            final BaseFont bfSubtitle = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, true);
            PdfContentByte cb = pdfWriter.getDirectContent();
            cb.saveState();
            cb.beginText();
            cb.setRGBColorStroke(0xFF, 0xFF, 0xFF);
            cb.setRGBColorFill(0xFF, 0xFF, 0xFF);
            cb.setCharacterSpacing(1f);
            cb.moveText(50, 575);
            cb.setFontAndSize(bfTitle, 26);
            cb.setTextRenderingMode(PdfContentByte.TEXT_RENDER_MODE_FILL_STROKE);
            cb.showText(title);
            cb.moveText(0, -35);
            cb.setTextRenderingMode(PdfContentByte.TEXT_RENDER_MODE_FILL);
            cb.setFontAndSize(bfSubtitle, 18);
            cb.showText(placeholderAccessor.getPlaceholderForDocument(DocetDocumentPlaceholder.PDF_COVER_SUBTITLE_1,
                docetLang));
            cb.moveText(0, -25);
            cb.setFontAndSize(bfSubtitle, 16);
            cb.showText(productName + " " + productVersion);
            cb.endText();
            cb.restoreState();
            document.close();
            reader = new PdfReader(baos.toByteArray());

        } catch (IOException | DocumentException e) {
            throw new DocetDocumentParsingException("Impossible to generate pdf", e);
        }
        docsToMerge.put(COVERPAGE_PAGE_ID, reader);
        docTitles.add(COVERPAGE_PAGE_ID);
    }

    private void parseSummaryForEntry(final SummaryEntry entry, final String packageName, final PdfCopy copy,
        final DocetExecutionContext ctx, final Map<String, PdfReader> docsToMerge, final String summaryIndex, final List<String> docTitles)
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
        docTitles.add(title);
        final PdfReader reader = new PdfReader(pdfParser.parsePage(rawHtml.toString()));
        docsToMerge.put(title, reader);

//        copy.addDocument(reader);
//        reader.close();
        for (final SummaryEntry subEntry: entry.getSubSummary()) {
            this.parseSummaryForEntry(subEntry, packageName, copy, ctx, docsToMerge, "###", docTitles);
        }
    }
}