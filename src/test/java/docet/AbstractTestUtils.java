package docet;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
        File docFolder = testFolder.newFolder("docs");
        createLanguageStructure("it", docFolder);
        createLanguageStructure("en", docFolder);
        createLanguageStructure("fr", docFolder);
    }

    private static void createLanguageStructure(final String lang, final File docRootFolder) throws IOException {
        final Path langFolder = Files.createDirectory(Paths.get(docRootFolder.toURI()).resolve(lang));
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
