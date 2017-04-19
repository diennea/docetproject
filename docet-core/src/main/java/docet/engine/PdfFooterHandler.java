package docet.engine;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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

public class PdfFooterHandler extends PdfPageEventHelper {
    
    private ElementList footer;
    private PdfOutline root;
    private List<TOCEntry> toc = new ArrayList<TOCEntry>();
    private final String HTML_FOOTER_STRUCTURE = "<table style=\"font-family:'Helvetica Neue', "
        + "Arial, sans-serif;font-size:12px;line-height:1.6em;color:#444;\" width=\"100%\" border=\"0\"><tr>"
        + "<td>${footerMessage}</td></tr></table>";
    
    public PdfFooterHandler(final String footerText) throws IOException {
        footer = XMLWorkerHelper.parseToElementList(HTML_FOOTER_STRUCTURE.replaceAll("\\$\\{footerMessage\\}", footerText), null);
    }

    @Override
    public void onEndPage(PdfWriter writer, Document document) {
        try {
            ColumnText ct = new ColumnText(writer.getDirectContent());
            ct.setSimpleColumn(36, 10, 559, 32);
            for (Element e : footer) {
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
        new PdfOutline(root, dest, text);
        TOCEntry entry = new TOCEntry();
        entry.action = PdfAction.gotoLocalPage(writer.getPageNumber(), dest, writer);
        entry.title = text;
        toc.add(entry);
    }
}