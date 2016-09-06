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
package docet.servlets;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import docet.engine.DocetManager;
import docet.error.DocetException;

/**
 *
 *
 *
 */
public class DocetServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(DocetServlet.class.getName());
    private static String NOT_FOUND_MESSAGE = "<div><p>Document not found!</p></div>";
    /**
     * Handles the HTTP <code>GET</code> method.
     *
     * @param request
     *            servlet request
     * @param response
     *            servlet response
     * @throws ServletException
     *             if a servlet-specific error occurs
     * @throws IOException
     *             if an I/O error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * Processes requests for both HTTP <code>GET</code> and <code>POST</code>
     * methods.
     *
     * @param request
     *            servlet request
     * @param response
     *            servlet response
     * @throws ServletException
     *             if a servlet-specific error occurs
     * @throws IOException
     *             if an I/O error occurs
     */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        response.setContentType("text/html; charset=UTF-8");
        try (PrintWriter out = response.getWriter();) {
            final Map<String, String[]> params = request.getParameterMap();
            DocetManager docetEngine = (DocetManager) request.getServletContext().getAttribute("docetEngine");
            final String docType = (String) request.getAttribute("mnDocType");
            final String lang = (String) request.getAttribute("mnDocLanguage");
            final String packageId = (String) request.getAttribute("mnPackageId"); 
            final String pageId = (String) request.getAttribute("pageId");
            String html = "";
            final DocetRequestType req = DocetRequestType.parseDocetRequestByName(docType);
            
            try {
                LOGGER.log(Level.INFO, "Request " + req + " docType '" + docType + "' lang '" + lang + "' packageId '" + packageId + "' pageId " + pageId);
                switch (req) {
                    case TYPE_TOC:
                        html = docetEngine.serveTableOfContentsForPackage(packageId, lang, params);
                        break;
                    case TYPE_MAIN:
                        html = docetEngine.serveMainPageForPackage(lang, packageId, params);
                        break;
                    case TYPE_PAGES:
                        html = docetEngine.servePageIdForLanguageForPackage(packageId, pageId, lang, false, params);
                        break;
                    case TYPE_FAQ:
                        html = docetEngine.servePageIdForLanguageForPackage(packageId, pageId, lang, true, params);
                        break;
                    default:
                        html = NOT_FOUND_MESSAGE;
                }
                out.write(html);
            } catch (DocetException ex) {
                LOGGER.log(Level.SEVERE, "Error on fulfilling request '" + req + "' package '" + packageId + "' pageid '" + pageId + "'" , ex);
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                out.write("Package: '"  + packageId + "'; Req: '" + req + "'; pageid: '" + pageId
                        + "'; ErrorCode: '" + ex.getCode() + "': ErrorMessage: " + ex.getMessage());
            }
        }
    }
}
