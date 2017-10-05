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
package docet;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import org.junit.runner.Description;

import docet.engine.DocetConfiguration;
import docet.engine.DocetManager;
import docet.error.DocetException;

public abstract class AbstractTestUtils {

    private static DocetManager manager;

    @ClassRule
    public static TemporaryFolder testFolder = new TemporaryFolder();

    @ClassRule
    public static TestRule testStarter = new TestRule() {
        @Override
        public org.junit.runners.model.Statement apply(org.junit.runners.model.Statement base, Description description) {

            final Properties prop = new Properties();
            try {
                final Class clazz = Class.forName(description.getClassName(), false, Thread.currentThread().getContextClassLoader());
                if (clazz.isAnnotationPresent(DocetOptions.class)) {
                    final DocetOptions options = (DocetOptions) clazz.getAnnotation(DocetOptions.class);
                    prop.setProperty("docet.base.dir", options.baseDocetPath());
                    prop.setProperty("docet.staticresources.additionalParams", options.docetStaticResAdditionalParams());
                    prop.setProperty("docet.zip.path", options.docetZipPath());
                    prop.setProperty("docet.linktofaq.pattern", options.linkToFaqPattern());
                    prop.setProperty("docet.linktoimage.pattern", options.linkToImagePattern());
                    prop.setProperty("docet.linktopage.pattern", options.linkToPagePattern());
                    prop.setProperty("docet.searchindex.path", options.searchIndexPath());
                }
                manager = new DocetManager(new DocetConfiguration(prop));
                return base;
            } catch (ClassNotFoundException | DocetException ex) {
                throw new RuntimeException(ex);
            }
        }
    };
    @BeforeClass
    public static void setUpManager() {
        try {
            createDocumentationStructure();
            manager.start();
        } catch (Exception ex) {
           throw new RuntimeException(ex);
        }
    }

    private static void createDocumentationStructure() throws IOException {
        Path docFolder = testFolder.newFolder("docs").toPath();
        createLanguageStructure("it", docFolder);
        createLanguageStructure("en", docFolder);
        createLanguageStructure("fr", docFolder);
    }

    private static void createLanguageStructure(final String lang, final Path docRootFolder) throws IOException {
        final Path langFolder = Files.createDirectory(docRootFolder).resolve(lang);
        Files.createDirectory(langFolder.resolve("pages"));
        Files.createDirectory(langFolder.resolve("faq"));
        Files.createDirectory(langFolder.resolve("imgs"));
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public static @interface DocetOptions {
        String docetZipPath();
        String baseDocetPath();
        String searchIndexPath();
        String linkToPagePattern();
        String linkToImagePattern();
        String linkToFaqPattern();
        String docetStaticResAdditionalParams();
    }
}
