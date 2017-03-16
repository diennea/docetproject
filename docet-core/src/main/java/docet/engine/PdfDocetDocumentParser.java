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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.parser.Tag;
import org.jsoup.select.Elements;

import com.itextpdf.text.BadElementException;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Image;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.pdf.codec.Base64;
import com.itextpdf.tool.xml.XMLWorker;
import com.itextpdf.tool.xml.XMLWorkerHelper;
import com.itextpdf.tool.xml.css.CssFile;
import com.itextpdf.tool.xml.css.StyleAttrCSSResolver;
import com.itextpdf.tool.xml.html.Tags;
import com.itextpdf.tool.xml.parser.XMLParser;
import com.itextpdf.tool.xml.pipeline.css.CSSResolver;
import com.itextpdf.tool.xml.pipeline.css.CssResolverPipeline;
import com.itextpdf.tool.xml.pipeline.end.PdfWriterPipeline;
import com.itextpdf.tool.xml.pipeline.html.AbstractImageProvider;
import com.itextpdf.tool.xml.pipeline.html.HtmlPipeline;
import com.itextpdf.tool.xml.pipeline.html.HtmlPipelineContext;

import docet.error.DocetDocumentParsingException;

public class PdfDocetDocumentParser implements DocetDocumentParser {

    private final CSSResolver cssRes;

    public PdfDocetDocumentParser(final String css) {
        this.cssRes = new StyleAttrCSSResolver();
        final CssFile cssFile;
        if (css != null) {
            cssFile = XMLWorkerHelper.getCSS(new ByteArrayInputStream(css.getBytes()));
        } else {
            cssFile = XMLWorkerHelper.getCSS(new ByteArrayInputStream(new byte[]{}));
        }
        cssRes.addCss(cssFile);
    }

    /**
     * {@inheritDoc}}
     */
    @Override
    public byte[] parsePage(final String html) throws DocetDocumentParsingException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();) {
            Document document = new Document(PageSize.A4);
            document.setMargins(30, 30, 50, 50);

            PdfWriter pdfWriter = PdfWriter.getInstance(document, baos);
            pdfWriter.setViewerPreferences(PdfWriter.PageModeUseOutlines);
            document.open();
            HtmlPipelineContext htmlContext = new HtmlPipelineContext(null);
            htmlContext.setTagFactory(Tags.getHtmlTagProcessorFactory());
            htmlContext.setImageProvider(new Base64ImageProvider());

            PdfWriterPipeline pdf = new PdfWriterPipeline(document, pdfWriter);
            HtmlPipeline htmlP = new HtmlPipeline(htmlContext, pdf);
            CssResolverPipeline css = new CssResolverPipeline(this.cssRes, htmlP);

            XMLWorker worker = new XMLWorker(css, true);
            XMLParser p = new XMLParser(worker);
            p.parse(new ByteArrayInputStream(this.sanitizeHtml(html).getBytes()));
            document.close();
            return baos.toByteArray();

        } catch (IOException | DocumentException e) {
            throw new DocetDocumentParsingException("Impossible to generate pdf", e);
        }
    }

    /**
     * needed because some css attributes are not supported by html elements used in our docs 
     * @param html
     * @return
     */
    private String sanitizeHtml(final String html) {
        org.jsoup.nodes.Document doc = Jsoup.parse(html, "", Parser.xmlParser());
        Elements divs = doc.select(".msg");
        for (Element div: divs) {
            div.replaceWith(this.createElementReplacement(div));
        }
        return doc.toString();
    }
 
    private Element createElementReplacement(final Element toreplace) {
        final Element table = new Element(Tag.valueOf("table"), "");
        final Element tr = new Element(Tag.valueOf("tr"), "");
        final Element td = new Element(Tag.valueOf("td"), "");
        td.html(toreplace.html());
        tr.appendChild(td);
        table.appendChild(tr);
        toreplace.classNames().stream().forEach(cssClass -> table.addClass(cssClass));
        return table;
    }
    private class Base64ImageProvider extends AbstractImageProvider {
 
        @Override
        public Image retrieve(String src) {
            int pos = src.indexOf("base64,");
            try {
                if (src.startsWith("data") && pos > 0) {
                    byte[] img = Base64.decode(src.substring(pos + 7));
                    return Image.getInstance(img);
                }
                else {
                    return Image.getInstance(src);
                }
            } catch (BadElementException ex) {
                return null;
            } catch (IOException ex) {
                return null;
            }
        }
 
        @Override
        public String getImageRootPath() {
            return null;
        }
    }
}
