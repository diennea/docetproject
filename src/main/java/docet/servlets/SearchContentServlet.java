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
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jackson.map.ObjectMapper;

import docet.DocetExecutionContext;
import docet.engine.DocetManager;
import docet.error.DocetException;
import docet.model.SearchResponse;

public class SearchContentServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(SearchContentServlet.class.getName());
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
        processRequest(request, response);
    }

    /**
     * Processes requests for both HTTP <code>GET</code> and <code>POST</code>
     * methods.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try (OutputStream out = response.getOutputStream();) {
            final DocetExecutionContext ctx = (DocetExecutionContext) request.getAttribute("docetContext");
            String sourcePackage = (String) request.getAttribute("mnCurrentPackage");
            try {
                DocetManager docetEngine = (DocetManager) request.getServletContext().getAttribute("docetEngine");
                final Map<String, String[]> additionalParams = new HashMap<>();
                request.getParameterMap().entrySet()
                        .stream()
                        .filter(entry -> !entry.getKey().equals("q") && !entry.getKey().equals("lang")
                                && !entry.getKey().equals("sourcePkg") && !entry.getKey().equals("enablePkg"))
                        .forEach(e -> {
                            additionalParams.put(e.getKey(), e.getValue());
                        });

                final Set<String> inScopePackages = new HashSet<>();
                inScopePackages.addAll(Arrays.asList((String[]) request.getAttribute("mnPackageList")));
                if (sourcePackage == null) {
                    sourcePackage = "";
                } else {
                    inScopePackages.add(sourcePackage);
                }
                final String query = (String) request.getAttribute("mnQuery");
                final String lang = (String) request.getAttribute("mnDocLanguage");
                LOGGER.log(Level.INFO, "Searching term '" + query + "' language '" + lang + "' refPackage'"
                        + sourcePackage + "' packageList " + inScopePackages);
                final SearchResponse searchResp = docetEngine.searchPagesByKeywordAndLangWithRerencePackage(query, lang,
                        sourcePackage, inScopePackages, additionalParams, ctx);
                String json = new ObjectMapper().writeValueAsString(searchResp);
                response.setContentType("application/json;charset=utf-8");
                response.getOutputStream().write(json.getBytes("utf-8"));
            } catch (DocetException ex) {
                LOGGER.log(Level.SEVERE, "Error on generating response", ex);
                final SearchResponse searchResponse = 
                        new SearchResponse(sourcePackage, SearchResponse.STATUS_CODE_FAILURE, ex.getCode(), ex.getMessage());
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                String json = new ObjectMapper().writeValueAsString(searchResponse);
                response.setContentType("application/json;charset=utf-8");
                response.getOutputStream().write(json.getBytes("utf-8"));
            }
        }
    }

}
