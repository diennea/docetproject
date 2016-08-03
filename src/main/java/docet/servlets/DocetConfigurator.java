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

import docet.engine.DocetConfiguration;
import docet.engine.DocetManager;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

public class DocetConfigurator implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent ctx) {
        ServletContext application = ctx.getServletContext();
        Properties configuration = new Properties();
        try {
            configuration.load(Thread.currentThread().getContextClassLoader().getResourceAsStream("docet.conf"));
            final Path docetBaseDir = Paths.get(configuration.getProperty("docet.base.dir", application.getRealPath("/")));
            //in case the provided path is relative use webapp base dir as docet.base.dir
            if (!docetBaseDir.isAbsolute()) {
                configuration.setProperty("docet.base.dir", Paths.get(application.getRealPath("/")).resolve(docetBaseDir).toString());
            }
            final Path docetIndexDir = Paths.get(configuration.getProperty("docet.searchindex.path", application.getRealPath("/")));
            //in case the provided path is relative use webapp base dir as docet.base.dir
            if (!docetIndexDir.isAbsolute()) {
                configuration.setProperty("docet.searchindex.path", Paths.get(application.getRealPath("/")).resolve(docetIndexDir).toString());
            }
            configuration.setProperty("docet.template.path", application.getRealPath("/"));
            final DocetConfiguration docetConf = new DocetConfiguration(configuration);
            final DocetManager manager = new DocetManager(docetConf);
            manager.start();
            application.setAttribute("docetEngine", manager);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        System.out.println("Shutting down DOCet");
        try {
            ((DocetManager) sce.getServletContext().getAttribute("docetEngine")).stop();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
