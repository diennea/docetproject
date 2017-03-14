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
import java.io.PrintWriter;

import javax.servlet.http.HttpServletResponse;

import docet.error.DocetDocumentParsingException;

public class HtmlDocetDocumentParser implements DocetDocumentParser {
    
    /**
     * {@inheritDoc}}
     */
    @Override
    public void parsePage(final String html, final HttpServletResponse resp) throws DocetDocumentParsingException {
        resp.setCharacterEncoding("UTF-8");
        resp.setContentType("text/html; charset=UTF-8");
        try (PrintWriter out = resp.getWriter();) {
            out.write(html);
        } catch (IOException e) {
            throw new DocetDocumentParsingException("Impossible to generate html for page", e);
        }
    }
    
}
