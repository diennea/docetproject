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

import docet.DocetDocumentGenerator;
import docet.DocetDocumentResourcesAccessor;
import docet.DocetExecutionContext;
import docet.DocetLanguage;
import docet.error.DocetDocumentParsingException;
import docet.error.DocetException;
import docet.model.DocetDocument;
import docet.model.SummaryEntry;

public class PDFDocumentGenerator implements DocetDocumentGenerator, DocetDocumentParser {

    private final DocetManager manager;

    public PDFDocumentGenerator(final DocetManager manager) throws IOException {
        this.manager = manager;
    }

    @Override
    public byte[] parsePage(String html, DocetDocumentResourcesAccessor accessor, DocetLanguage language)
            throws DocetDocumentParsingException {

        PDFDocumentHandler handler = PDFDocumentHandler
            .builder()
            .language(language)
            .placeholders(accessor)
            .cover(false)
            .toc(false)
            .bookmarks(false)
            .create();

        /* No need of title or page, just a single page without toc */
        handler.addSection(html, "", "", null);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        handler.createPDF(out);

        return out.toByteArray();
    }

    @Override
    public void generateDocetDocument(
            DocetDocument document,
            DocetExecutionContext ctx,
            OutputStream out,
            DocetDocumentResourcesAccessor placeholderAccessor) throws DocetDocumentParsingException {

        final DocetLanguage language = DocetLanguage.parseDocetLanguageByName(document.getLang());

        PDFDocumentHandler handler = PDFDocumentHandler
            .builder()
//            .debug()
            .language(language)
            .placeholders(placeholderAccessor)
            .title(document.getTitle())
            .create();

        addDocument(handler, document, ctx, language);

        handler.createPDF(out);

    }

    private void addDocument(
            PDFDocumentHandler handler,
            DocetDocument document,
            DocetExecutionContext ctx,
            DocetLanguage language) throws DocetDocumentParsingException {

        final String packageName = document.getPackageName();
        for(SummaryEntry entry : document.getSummary()) {
            handleSummaryEntry(handler, entry, packageName, ctx, null);
        }
    }

    private void handleSummaryEntry(
            final PDFDocumentHandler handler,
            final SummaryEntry entry,
            final String packageName,
            final DocetExecutionContext ctx,
            final String parentId) throws DocetDocumentParsingException {

        final String id = entry.getTargetPageId();
        final String lang = entry.getLang();

        final String html;
        try {
            html = this.manager.servePageIdForLanguageForPackage(
                    packageName, id, lang, DocetDocFormat.TYPE_PDF, false, null, ctx);
        } catch (DocetException e) {
            throw new DocetDocumentParsingException("Cannot retrieve page " + id, e);
        }

        handler.addSection(html, id, entry.getName(), parentId);

        for (final SummaryEntry subEntry : entry.getSubSummary()) {
            handleSummaryEntry(handler, subEntry, packageName, ctx, id);
        }
    }
}
