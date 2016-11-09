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

    @Parameter(property = "basedir", defaultValue = "${project.basedir}")
    private String basedir;

    @Parameter(property = "outputdir", defaultValue = "${project.build.directory}/docet")
    private String outputdir;

    @Parameter(property = "classesdir", defaultValue = "${project.build.directory}/classes")
    private String classesdir;

    @Parameter(property = "sourcedir", defaultValue = "src/main/docs")
    private String sourcedir;

    @Parameter(property = "noindex", defaultValue = "false")
    private boolean noindex;

    @Parameter(property = "zip", defaultValue = "true")
    private boolean zip;

    @Parameter(property = "bundlezip", defaultValue = "false")
    private boolean bundlezip;

    @Parameter(property = "zipfilename", defaultValue = "documentation.zip")
    private String zipfilename;

    @Parameter(property = "skipvalidation", defaultValue = "false")
    private boolean skipvalidation;

    @Parameter(property = "attach", defaultValue = "true")
    private boolean attach;

    /**
     * Maven ProjectHelper.
     */
    @Component
    private MavenProjectHelper projectHelper;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    MavenProject project;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        final DocetPluginUtils.Holder<Integer> errors = new DocetPluginUtils.Holder<>(0);
        final DocetPluginUtils.Holder<Integer> warnings = new DocetPluginUtils.Holder<>(0);
        final Path outDirPath = Paths.get(outputdir);
        final Path indexDirPath = outDirPath.resolve(DEFAULT_INDEX_BASEDIR);

        getLog().info("Base directory (basedir): " + basedir);
        getLog().info("Source directory (sourcedir): " + sourcedir);
        getLog().info("Output directory (outputdir): " + outputdir);
        getLog().info("Classes directory (classesDir): " + classesdir);
        Path baseDirPath = Paths.get(basedir);
        Path classesDirPath = Paths.get(classesdir);

        final Path srcDir = baseDirPath.resolve(sourcedir);
        if (!Files.isReadable(srcDir)) {
            throw new MojoFailureException(
                "Document directory '" + srcDir.toAbsolutePath() + "' does not exist or is not readable, please check the path");
        }

        try {
            if (!Files.isDirectory(outDirPath)) {
                Files.createDirectories(outDirPath);
            }
        } catch (IOException e) {
            throw new MojoFailureException("Error while generating output directory", e);
        }

        Date start = new Date();

        Map<Language, List<FaqEntry>> faqs = new EnumMap<>(Language.class);

        if (this.skipvalidation) {
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
            if (!this.noindex) {
                getLog().error("--- Indexing DOCet source files: SKIPPED due to VALIDATION ERRORS");
            }
            if (this.zip) {
                getLog().error("--- Zipping DOCet disabled: SKIPPED due to VALIDATION ERRORS");
            }
        } else {
            if (this.noindex) {
                getLog().info("--- Indexing DOCet disabled: SKIPPING");
            } else {
                getLog().info("Index directory: " + indexDirPath.toAbsolutePath());
                getLog().info("--- Indexing DOCet source files...");
                try {
                    if (!Files.isDirectory(indexDirPath)) {
                        Files.createDirectories(indexDirPath);
                    }
                } catch (IOException e) {
                    throw new MojoFailureException("Error while generating index directory", e);
                }
                DocetPluginUtils.indexDocs(indexDirPath, srcDir, faqs, getLog());
            }
            if (!this.zip) {
                getLog().info("--- Zipping DOCet disabled: SKIPPING");
            } else {
                getLog().info("bundleZip: " + bundlezip);
                Path zipFile = bundlezip ? classesDirPath.resolve(zipfilename) : outDirPath.resolve(zipfilename);
                try {
                    if (!Files.isDirectory(zipFile.getParent())) {
                        Files.createDirectories(zipFile.getParent());
                    }
                } catch (IOException e) {
                    throw new MojoFailureException("Error while generating zip output directory", e);
                }

                getLog().info("--- Zipping DOCet docet docs and index...to " + zipFile.toAbsolutePath());
                final int zippedNo = DocetPluginUtils.zippingDocs(this.skipvalidation, srcDir, outDirPath, indexDirPath, !this.noindex, zipFile, faqs, getLog());
                getLog().info(zippedNo + " files added to archive '" + zipFile.toAbsolutePath() + "'");
                if (this.attach) {
                    getLog().info("--- Installing DOCet zip artifact '" + zipFile.toAbsolutePath() + "'");
                    getLog().info("project: " + this.project);
                    this.projectHelper.attachArtifact(this.project, "zip", "docs", zipFile.toFile());
                } else {
                    getLog().info("--- Installing DOCet zip artifact '" + zipFile.toAbsolutePath() + "': DISABLED");
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
