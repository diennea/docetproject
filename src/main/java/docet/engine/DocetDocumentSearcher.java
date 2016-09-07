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
import java.util.List;

import docet.error.DocetDocumentSearchException;
import docet.model.DocetDocument;

/**
 * Interface of a Document searcher.
 *
 * @author matteo.casadei
 *
 */
public interface DocetDocumentSearcher {

    List<DocetDocument>  searchForMatchingDocuments(final String searchText, final String lang, final int maxNumResults)
        throws DocetDocumentSearchException;

    DocetDocument  searchDocumentById(final String searchText, final String lang)
        throws DocetDocumentSearchException;

    /**
     *
     * @return true if the searcher is actually open, false in case the underlying search index is already open.
     *
     * @throws IOException
     */
    boolean open() throws IOException;

    /**
    *
    * @return true if the search is closed, false in case the underlying search index is already closed.
    *
    * @throws IOException
    */
    boolean close() throws IOException;

}
