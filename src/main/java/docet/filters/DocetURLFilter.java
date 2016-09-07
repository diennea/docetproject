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
package docet.filters;

import java.io.IOException;
import java.util.Optional;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import docet.DocetExecutionContext;
import docet.engine.DocetConfiguration;
import docet.servlets.DocetRequestType;

public class DocetURLFilter implements Filter {

    private String urlPattern;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        this.urlPattern = filterConfig.getInitParameter("expectedUrlPattern");

    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpReq = (HttpServletRequest) request;
        final String reqPath = httpReq.getServletPath();
        if(reqPath.matches(this.urlPattern)) {
            String[] tokens = reqPath.substring(1).split("/");
            httpReq.setAttribute("mnDocType", tokens[0]);
            String lang = Optional.ofNullable(httpReq.getParameter("lang")).orElse("it");
            final DocetRequestType req = DocetRequestType.parseDocetRequestByName(tokens[0]);
            final String packageId;
            if (tokens.length > 1) {
                packageId = tokens[1];
            } else {
                packageId = null;
            }
            httpReq.setAttribute("mnPackageId", packageId);
            switch(req) {
            case TYPE_TOC:
                final String packageIdParam = httpReq.getParameter("packageId");
                if (packageIdParam == null) {
                    final HttpServletResponse resp = (HttpServletResponse) response;
                    resp.reset();
                    resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }
                httpReq.setAttribute("mnPackageId", packageIdParam);
            case TYPE_MAIN:
                httpReq.setAttribute("mnDocLanguage", lang);
                break;
            case TYPE_FAQ:
            case TYPE_PAGES:
                String[] pageFields = tokens[2].split("_");
                final String pageName = pageFields[1];
                if (pageName.endsWith(".mndoc")) {
                    lang = pageName.split(".mndoc")[0];
                } else if (pageName.endsWith(".pdf")) {
                    lang = pageName.split(".pdf")[0];
                }
                httpReq.setAttribute("mnDocLanguage", lang);
                httpReq.setAttribute("pageId", pageFields[0]);
                break;
            case TYPE_IMAGES:
                String[] imgFields = tokens[2].split("_");
                lang = imgFields[0];
                final String imgName = imgFields[1].split(".mnimg")[0];
                httpReq.setAttribute("mnDocLanguage", lang);
                httpReq.setAttribute("imageName", imgName);
                break;
            case TYPE_SEARCH:
                httpReq.setAttribute("mnCurrentPackage", httpReq.getParameter("sourcePkg"));
                httpReq.setAttribute("mnPackageList", request.getParameterValues("enablePkg[]"));
                httpReq.setAttribute("mnDocLanguage", lang);
                httpReq.setAttribute("mnQuery", httpReq.getParameter("q"));
                break;
            case TYPE_PACKAGE:
                httpReq.setAttribute("mnDocLanguage", lang);
                httpReq.setAttribute("mnPackageIds", httpReq.getParameterValues("id"));
                break;
            default:
                final HttpServletResponse resp = (HttpServletResponse) response;
                resp.reset();
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            final DocetExecutionContext ctx = new DocetExecutionContext(httpReq);
            this.initializePackageAccessPermission(
                ctx, (DocetConfiguration) httpReq.getServletContext().getAttribute("docetConfiguration"));
            httpReq.setAttribute("docetContext", ctx);
        } else {
            final HttpServletResponse resp = (HttpServletResponse) response;
            resp.reset();
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        chain.doFilter(request, response);
    }

    private void initializePackageAccessPermission(final DocetExecutionContext ctx, final DocetConfiguration conf) {
        conf.getInstalledPackages()
            .stream()
            .forEach(pkg -> ctx.setAccessPermission(pkg, DocetExecutionContext.AccessPermission.ALLOW));
    }

    @Override
    public void destroy() {
        // TODO Auto-generated method stub

    }

}
