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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;

import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.pdf.PdfCopy;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.tool.xml.XMLWorkerHelper;

import docet.engine.model.TOC.TOCItem;

public class PDFDocetDocumentWriter extends BaseDocetDocumentWriter {

    private PdfCopy copy;
    private Document document;

    @Override
    public void renderPage(TOCItem tocItem, String parentTocIndex, OutputStream out) throws Exception {
        PdfReader reader = new PdfReader(parseHtml(tocItem.getPagePath()));
        this.copy.addDocument(reader);
        reader.close();
    }

    private byte[] parseHtml(String html) throws DocumentException, IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // step 1
        final Document pdfDoc = new Document();
        // step 2
        PdfWriter writer = PdfWriter.getInstance(pdfDoc, baos);
        // step 3
        pdfDoc.open();
        // step 4
        XMLWorkerHelper.getInstance().parseXHtml(writer, pdfDoc,
                new FileInputStream(html));
        // step 5
        pdfDoc.close();
        // return the bytes of the PDF
        return baos.toByteArray();
    }

    @Override
    public void initializeOutputDocument(OutputStream out) throws Exception {
        this.document = new Document();
        this.copy = new PdfCopy(document, out);
        document.open();
    }

    @Override
    public void finalizeOutputDocument(OutputStream out) {
        document.close();
    }

}
