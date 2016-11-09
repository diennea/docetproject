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
package docet.maven;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import docet.engine.model.FaqEntry;
import docet.maven.DocetPluginUtils.Language;

/**
*
* This mojo takes care of generating pdf docet docs.
*
* @author matteo.casadei.
*
*/
@Mojo(name = "generatepdf")
public class DocetPdfGenerationMojo extends AbstractMojo {

    @Parameter(property = "outputdir", defaultValue = "target")
    private String outputDir;

    @Parameter(property = "sourcedir", defaultValue = "src/docs")
    private String sourceDir;

    @Parameter(property = "skipvalidation", defaultValue = "false")
    private boolean skipValidation;

    @Parameter(property = "lang", defaultValue = "it")
    private String lang;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        final DocetPluginUtils.Holder<Integer> errors = new DocetPluginUtils.Holder<>(0);
        final DocetPluginUtils.Holder<Integer> warnings = new DocetPluginUtils.Holder<>(0);
        getLog().info("Source directory: " + sourceDir);
        getLog().info("Output directory: " + outputDir + "/pdf");
        getLog().info("Language: " + lang);

        final Path srcDir = Paths.get(sourceDir);
        if (!Files.isReadable(srcDir)) {
            throw new MojoFailureException(
                    "Document directory '" + srcDir.toAbsolutePath() + "' does not exist or is not readable, please check the path");
        }
        final Path outDirPath = Paths.get(outputDir).resolve("pdf").resolve(lang);
        try {
            if (!Files.exists(outDirPath)) {
                Files.createDirectories(outDirPath);
            }
        } catch (IOException e) {
            throw new MojoFailureException("Error while generating output directory", e);
        }

        final Path outTmpPath = Paths.get(outputDir).resolve("tmp");
        try {
            if (!Files.exists(outTmpPath)) {
                Files.createDirectories(outTmpPath);
            }
        } catch (IOException e) {
            throw new MojoFailureException("Error while generating output temporary directory", e);
        }

        Date start = new Date();

        Map<Language, List<FaqEntry>> faqs = new EnumMap<>(Language.class);

        if (this.skipValidation) {
            getLog().info("--- Preemptive DOCet source files validation: DISABLED");
        } else {
            getLog().info("--- Preemptive DOCet source files validation...");
            Map<DocetPluginUtils.Language, List<DocetIssue>> results = DocetPluginUtils.validateDocs(srcDir, faqs, getLog());
            results.entrySet().stream().forEach(e -> {
                e.getValue().forEach(issue -> {
                    if (issue.getSeverity() == Severity.ERROR) {
                        getLog().error("[" + e.getKey() + "] -> " + issue.getMsg());
                        errors.setValue(errors.getValue() + 1);
                    } else if (issue.getSeverity() == Severity.WARN) {
                        getLog().warn("[" + e.getKey() + "] -> " + issue.getMsg());
                        warnings.setValue(warnings.getValue() + 1);
                    }
                });
            });
        }

        if (errors.getValue() == 0) {
            getLog().info("--- Generating PDFs....");
            Map<DocetPluginUtils.Language, List<DocetIssue>> results = DocetPluginUtils.generatePdfsForLanguage(srcDir, outDirPath, outTmpPath, lang, getLog());
            results.entrySet().stream().forEach(e -> {
                e.getValue().forEach(issue -> {
                    if (issue.getSeverity() == Severity.ERROR) {
                        getLog().error("[" + e.getKey() + "] -> " + issue.getMsg());
                        errors.setValue(errors.getValue() + 1);
                    } else if (issue.getSeverity() == Severity.WARN) {
                        getLog().warn("[" + e.getKey() + "] -> " + issue.getMsg());
                        warnings.setValue(warnings.getValue() + 1);
                    }
                });
            });
        }

        Date end = new Date();
        String warnMessage = "";
        if (warnings.getValue() > 0) {
            warnMessage = "Warnings: " + warnings.getValue();
        }
        String errorMessage = "";
        if (errors.getValue() > 0) {
            errorMessage = "Errors: " + errors.getValue();
        }
        getLog().info("Total execution time: " + (end.getTime() - start.getTime()) + "ms \n\t" + warnMessage + "\n\t" + errorMessage);

        if (errors.getValue() > 0) {
            throw new MojoFailureException("Validation ended up with" + errors.getValue() + " errors!");
        }


    }

}
