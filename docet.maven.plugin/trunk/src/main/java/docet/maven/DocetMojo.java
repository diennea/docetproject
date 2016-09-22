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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;

import docet.engine.model.FaqEntry;
import docet.maven.DocetPluginUtils.Language;

/**
 *
 * This mojo takes care of indexing docet src docs.
 *
 * @author matteo.casadei.
 *
 */
@Mojo(name = "builddoc")
public class DocetMojo extends AbstractMojo {

    private static final String DEFAULT_INDEX_BASEDIR = "index";

    @Parameter(property = "outputdir", defaultValue = "target")
    private String outputDir;

    @Parameter(property = "sourcedir", defaultValue = "src/docs")
    private String sourceDir;

    @Parameter(property = "noindex", defaultValue = "false")
    private boolean noIndex;

    @Parameter(property = "nozip", defaultValue = "false")
    private boolean noZip;

    @Parameter(property = "zipfilename", defaultValue = "docetdocs.zip")
    private String zipFileName;

    @Parameter(property = "skipvalidation", defaultValue = "false")
    private boolean skipValidation;

    @Parameter(property = "skipattach", defaultValue = "false")
    private boolean skipAttach;

    /**
     * Maven ProjectHelper.
     */
    @Component
    private MavenProjectHelper projectHelper;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    MavenProject project;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        final String indexDir = this.outputDir + "/" + DEFAULT_INDEX_BASEDIR;
        final DocetPluginUtils.Holder<Integer> errors = new DocetPluginUtils.Holder<>(0);
        final DocetPluginUtils.Holder<Integer> warnings = new DocetPluginUtils.Holder<>(0);
        getLog().info("Source directory: " + sourceDir);
        getLog().info("Output directory: " + outputDir);
        getLog().info("Index directory: " + indexDir);

        final Path srcDir = Paths.get(sourceDir);
        if (!Files.isReadable(srcDir)) {
            throw new MojoFailureException(
                    "Document directory '" + srcDir.toAbsolutePath() + "' does not exist or is not readable, please check the path");
        }
        final Path outDirPath = Paths.get(outputDir);
        try {
            if (!Files.exists(outDirPath)) {
                Files.createDirectory(outDirPath);
            }
        } catch (IOException e) {
            throw new MojoFailureException("Error while generating output directory", e);
        }
        final Path indexDirPath = Paths.get(indexDir);

        Date start = new Date();

        Map<Language, List<FaqEntry>> faqs = new HashMap<>();

        if (this.skipValidation) {
            getLog().info("--- Validating DOCet source files: SKIPPED");
        } else {
            getLog().info("--- Validating DOCet source files...");
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

        if (errors.getValue() > 0) {
            if (!this.noIndex) {
                getLog().error("--- Indexing DOCet source files: SKIPPED due to VALIDATION ERRORS");
            }
            if (!this.noZip) {
                getLog().error("--- Zipping DOCet disabled: SKIPPED due to VALIDATION ERRORS");
            }
        } else {
            if (this.noIndex) {
                getLog().info("--- Indexing DOCet disabled: SKIPPING");
            } else {
                getLog().info("--- Indexing DOCet source files...");
                DocetPluginUtils.indexDocs(indexDirPath, srcDir, faqs, getLog());
            }

            if (this.noZip) {
                getLog().info("--- Zipping DOCet disabled: SKIPPING");
            } else {
                getLog().info("--- Zipping DOCet docet docs and index...");
                final int zippedNo = DocetPluginUtils.zippingDocs(this.skipValidation, srcDir, outDirPath, indexDirPath, !this.noIndex, zipFileName, faqs, getLog());
                getLog().info(zippedNo + " files added to archive '" + this.zipFileName + "'");
                if (this.skipAttach) {
                    getLog().info("--- Installing DOCet zip artifact '" + this.zipFileName + "': DISABLED");
                } else {
                    getLog().info("--- Installing DOCet zip artifact '" + this.zipFileName + "'");
                    getLog().info("project: " + this.project);
                    this.projectHelper.attachArtifact(this.project, "zip", null, outDirPath.resolve(zipFileName).toFile());
                }
            }
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
            throw new MojoFailureException("Building ended up with" + errors.getValue() + " errors!");
        }
    }
}
