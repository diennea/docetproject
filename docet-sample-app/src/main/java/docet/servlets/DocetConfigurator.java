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
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import docet.engine.DocetConfiguration;
import docet.engine.DocetManager;
import docet.error.DocetException;

public class DocetConfigurator implements ServletContextListener {

    private static final Logger LOGGER = Logger.getLogger(DocetConfigurator.class.getName());

    @Override
    public void contextInitialized(ServletContextEvent ctx) {
        LOGGER.log(Level.SEVERE, "Docet is starting");
        ServletContext application = ctx.getServletContext();
        Properties configuration = new Properties();
        Properties docPackages = new Properties();
        try {
            configuration.load(Thread.currentThread().getContextClassLoader().getResourceAsStream("docet.conf"));
            docPackages.load(Thread.currentThread().getContextClassLoader().getResourceAsStream("docet-packages.conf"));
            final Path docetBaseDir = Paths.get(configuration.getProperty("docet.base.dir", application.getRealPath("/")));
            configuration.setProperty("docet.base.dir", Paths.get(application.getRealPath("/")).resolve(docetBaseDir).toString());

            final Path docetIndexDir = Paths.get(configuration.getProperty("docet.searchindex.path", application.getRealPath("/")));
            //in case the provided path is relative use webapp base dir as docet.base.dir
            if (!docetIndexDir.isAbsolute()) {
                configuration.setProperty("docet.searchindex.path", Paths.get(application.getRealPath("/")).resolve(docetIndexDir).toString());
            }
            configuration.setProperty("docet.template.path", application.getRealPath("/"));
            final DocetConfiguration docetConf = new DocetConfiguration(configuration);
            //as we are in debug mode we just add straight the path to doc packages in working space
            docPackages.entrySet().stream().forEach(docPackage -> {
                Path packagePath = Paths.get(docPackage.getValue().toString());
                if (!packagePath.isAbsolute()) {
                    packagePath = Paths.get(application.getRealPath("/")).resolve(packagePath);
                }
                docetConf.addPackage(docPackage.getKey().toString(), packagePath.toString());
            });
            final DocetManager manager = new DocetManager(docetConf);
            manager.start();
            application.setAttribute("docetEngine", manager);
            application.setAttribute("docetConfiguration", docetConf);
            LOGGER.log(Level.SEVERE, "Docet configured, config:" + docetConf);
        } catch (DocetException | IOException e) {
            LOGGER.log(Level.SEVERE, "Impossible to properly setting up Docet configuration for Manager. ", e);
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        LOGGER.log(Level.INFO, "DOCet is shutting DOWN");
        try {
            ((DocetManager) sce.getServletContext().getAttribute("docetEngine")).stop();
        } catch (InterruptedException e) {
            LOGGER.log(Level.SEVERE, "Error on shutting down Docet. ", e);
            Thread.currentThread().interrupt();
        }
    }

}
