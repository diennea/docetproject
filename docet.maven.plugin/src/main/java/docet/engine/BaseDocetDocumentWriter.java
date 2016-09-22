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

import java.io.OutputStream;

import org.apache.cxf.common.i18n.UncheckedException;

import docet.engine.model.TOC;

/**
 * A base implementation for a document writer.
 *<p>
 *
 *Needs to be specialized with the actual implementation of a concrete writer for pages.
 *
 * @author matteo.casadei
 *
 */
public abstract class BaseDocetDocumentWriter implements DocetDocumentWriter {

    @Override
    public void renderDocetDocument(final TOC toc, final OutputStream out) throws Exception {
        this.initializeOutputDocument(out);
        toc.getItems().stream().forEach(item -> {
            final String index = "";
            try {
                this.renderPageSubtree(item, index, out);
            } catch (Exception e) {
                throw new UncheckedException(e);
            }
        });
        this.finalizeOutputDocument(out);

    }

    private void renderPageSubtree(final TOC.TOCItem item, final String parentTocIndex,
            final OutputStream out) throws Exception {
        this.renderPage(item, parentTocIndex, out);
        if (item.getSubItems().isEmpty()) {
            return;
        }

        int sequenceNum = 1;
        for (final TOC.TOCItem current : item.getSubItems()) {
            this.renderPageSubtree(current, parentTocIndex + "." + sequenceNum, out);
            sequenceNum++;
        }
    }

    @Override
    public abstract void renderPage(final TOC.TOCItem tocItem, final String parentTocIndex, final OutputStream out) throws Exception;

    @Override
    public abstract void initializeOutputDocument(final OutputStream out) throws Exception;

    @Override
    public abstract void finalizeOutputDocument(final OutputStream out);

}

