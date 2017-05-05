package docet.servlets;

import java.io.IOException;

import docet.DocetDocumentPlaceholder;
import docet.DocetLanguage;
import docet.SimplePackageLocator;
import docet.engine.DocetConfiguration;

public class DocetSamplePackageLocator extends SimplePackageLocator {
    
    public DocetSamplePackageLocator(final DocetConfiguration docetConf) throws IOException {
        super(docetConf);
    }
    
    @Override
    public String getPlaceholderForPdfDocument(final DocetDocumentPlaceholder placeholder, final DocetLanguage lang) {
        final String res;
        switch (placeholder) {
            case PDF_FOOTER_COVER:
                res = "This is a sample app - &copy; Copyright 2017";
                break;
            default:
                res = "";
        }
        return res;
    }
}
