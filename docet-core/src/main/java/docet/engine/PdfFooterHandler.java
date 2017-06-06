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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.ExceptionConverter;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.ColumnText;
import com.itextpdf.text.pdf.PdfAction;
import com.itextpdf.text.pdf.PdfDestination;
import com.itextpdf.text.pdf.PdfOutline;
import com.itextpdf.text.pdf.PdfPageEventHelper;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.tool.xml.ElementList;
import com.itextpdf.tool.xml.XMLWorkerHelper;

import docet.DocetUtils;

public class PdfFooterHandler extends PdfPageEventHelper {

    private static final String DEFAULT_FOOTER_COVER_BACKGROUND_COLOR = "";
    private static final String DEFAULT_FOOTER_COVER_FONT_SIZE = "12";
    private static final String DEFAULT_FOOTER_COVER_TEXT_COLOR = "#FFF";

    private Integer[] footerBackground;
    private static final String HTML_FOOTER_PAGE_STRUCTURE = "<table style=\"font-family:'Helvetica Neue', "
        + "Arial, sans-serif;font-size:${fontSize}px;line-height:1.6em;color:${textColor};"
        + "\" width=\"100%\" border=\"0\"><tr>"
        + "<td>${footerMessage}</td></tr></table>";
    private ElementList pageFooter;
    private List<TOCEntry> toc = new ArrayList<TOCEntry>();

    public PdfFooterHandler() throws IOException {
        this("", "");
        this.footerBackground = new Integer[]{};
    }

    public PdfFooterHandler(final String pageFooterText, final String coverFooterText) throws IOException {
        this.updateFooter(pageFooterText);
    }

    @Override
    public void onEndPage(PdfWriter writer, Document document) {
        try {
            if (this.footerBackground.length == 3) {
                Rectangle rect1 = new Rectangle(document.getPageSize().getWidth(), 0, 0, 40);
                rect1.setBackgroundColor(new BaseColor(this.footerBackground[0], this.footerBackground[1], this.footerBackground[2]));
                writer.getDirectContentUnder().rectangle(rect1);
            }
            ColumnText ct = new ColumnText(writer.getDirectContent());
            ct.setSimpleColumn(36, 5, 559, 27);
            for (Element e : pageFooter) {
                ct.addElement(e);
            }
            ct.go();
        } catch (DocumentException de) {
            throw new ExceptionConverter(de);
        }
    }

    @Override
    public void onGenericTag(PdfWriter writer, Document document, Rectangle rect, String text) {
        PdfDestination dest = new PdfDestination(PdfDestination.XYZ, rect.getLeft(), rect.getTop(), 0);
//        new PdfOutline(root, dest, text);
        TOCEntry entry = new TOCEntry();
        entry.action = PdfAction.gotoLocalPage(writer.getPageNumber(), dest, writer);
        entry.title = text;
        toc.add(entry);
    }

    public void updateFooter(final String footerText) throws IOException {
        pageFooter = XMLWorkerHelper.parseToElementList(HTML_FOOTER_PAGE_STRUCTURE.replaceAll("\\$\\{footerMessage\\}", footerText)
            .replaceAll("\\$\\{textColor\\}", DEFAULT_FOOTER_COVER_TEXT_COLOR)
            .replaceAll("\\$\\{fontSize\\}", DEFAULT_FOOTER_COVER_FONT_SIZE),null);
    }

    public void updateFooter(final String footerText, final String textColor, final String backgroundColor,
        final String fontSize) throws IOException {
        String computedTextColor;
        String computedBckColor;
        String computedFontSize;
        String computedFooterText = Optional.ofNullable(footerText).orElse("");

        computedTextColor = Optional.ofNullable(textColor).orElse(DEFAULT_FOOTER_COVER_TEXT_COLOR);
        if (computedTextColor.isEmpty()) {
            computedTextColor = DEFAULT_FOOTER_COVER_TEXT_COLOR;
        }
        computedBckColor = Optional.ofNullable(backgroundColor).orElse(DEFAULT_FOOTER_COVER_BACKGROUND_COLOR);
        if (computedBckColor.isEmpty()) {
            computedBckColor = DEFAULT_FOOTER_COVER_BACKGROUND_COLOR;
        }
        computedFontSize = Optional.ofNullable(fontSize).orElse(DEFAULT_FOOTER_COVER_FONT_SIZE);
        if (computedFontSize.isEmpty()) {
            computedFontSize = DEFAULT_FOOTER_COVER_FONT_SIZE;
        }
        final String footer = HTML_FOOTER_PAGE_STRUCTURE.replaceAll("\\$\\{footerMessage\\}", computedFooterText)
            .replaceAll("\\$\\{textColor\\}", computedTextColor)
            .replaceAll("\\$\\{fontSize\\}", computedFontSize);
        pageFooter = XMLWorkerHelper.parseToElementList(footer, null);
        this.footerBackground = DocetUtils.convertHexColorToRgb(computedBckColor);
    }
}