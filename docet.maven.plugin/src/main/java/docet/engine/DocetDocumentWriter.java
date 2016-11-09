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

import docet.engine.model.TOC;

/**
 *
 * This defines a base interface for a writer of complete Docet doc.
 *<p>
 *
 * A complete Docet doc is one encompassing a whole main section (from toc) along with every subsection.
 *
 * @author matteo.casadei
 *
 */
public interface DocetDocumentWriter {

    /**
     * Used to initialize the output document before starting to write it.
     *
     * @param out the output stream where to write the rendered doc
     *
     * @throws Exception in case of initialization issues
     */
    void initializeOutputDocument(final OutputStream out) throws Exception;

    /**
     * Used to finalize the output document after writing is complete.
     *
     * @param out the output stream where to write the rendered doc
     */
    void finalizeOutputDocument(final OutputStream out);

    /**
     * This renders a whole docet doc to a provided output stream.
     *
     * @param toc the toc describing the structure of the doc to be generated
     * @param out the output stream where to write the rendered doc
     *
     * @throws Exception in case of any rendering issues
     *
     */
    void renderDocetDocument(final TOC toc, final OutputStream out) throws Exception;

    /**
     * Used to render a page of the document.
     *
     * @param tocItem The tocItem describing the page to render
     * @param parentTocIndex index of the this page's parent page on the toc
     * @param out the output stream where to write the rendered page
     *
     * @throws Exception in case of issue rendering a page
     */
    void renderPage(final TOC.TOCItem tocItem, final String parentTocIndex, final OutputStream out) throws Exception;

}
