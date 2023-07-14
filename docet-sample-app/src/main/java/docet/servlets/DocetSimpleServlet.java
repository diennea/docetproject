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
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import docet.engine.DocetManager;
import docet.error.DocetException;

/**
 *
 *
 *
 */
public class DocetSimpleServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(DocetSimpleServlet.class.getName());

    /**
     * Handles the HTTP <code>GET</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        System.out.println("ServletPath:" + request.getServletPath() + " " + request.getContextPath() + " " + request.getRequestURI());
        try {
            processRequest(request, response);
        } catch (DocetException ex) {
            LOGGER.log(Level.SEVERE, "Error on serving request, go to error page", ex);
            throw new ServletException(ex);
        }
    }

    /**
     * Processes requests for both HTTP <code>GET</code> and <code>POST</code> methods.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws DocetException in case errors occurs on handling doc requests
     */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response) throws DocetException {
        DocetManager docetEngine = (DocetManager) request.getServletContext().getAttribute("docetEngine");
        System.out.println("ServletPath:" + request.getServletPath() + " " + request.getContextPath() + " " + request.getRequestURI());
        docetEngine.serveRequest(request, response, new DocetSampleDocumentAccessor());
    }
}
