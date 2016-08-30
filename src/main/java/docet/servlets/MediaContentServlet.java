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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;

import javax.imageio.ImageIO;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import docet.engine.DocetManager;

public class MediaContentServlet extends HttpServlet {

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
        try (OutputStream out = response.getOutputStream();) {
            try {
                DocetManager docetEngine = (DocetManager) request.getServletContext().getAttribute("docetEngine");
                final String lang = (String) request.getAttribute("mnDocLanguage");
                final String imageName = (String) request.getAttribute("imageName");
                final String imageFormat = imageName.substring(imageName.indexOf(".") + 1);
                final String packageId = (String) request.getAttribute("mnPackageId");
                switch (imageFormat) {
                    case "gif":
                        response.setContentType("image/gif");
                        break;
                    case "png":
                        response.setContentType("image/png");
                        break;
                    case "jpg":
                    case "jpeg":
                        response.setContentType("image/jpeg");
                        break;
                    default:
                        throw new FileNotFoundException("Unsupported image type " + imageFormat);
                }
                docetEngine.getImageBylangForPackage(imageName, lang, packageId, out);
            } catch (Exception e) {
                e.printStackTrace();
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            }
        }
    }

}
