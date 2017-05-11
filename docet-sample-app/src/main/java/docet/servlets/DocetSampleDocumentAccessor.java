package docet.servlets;

import docet.DocetDocumentPlaceholder;
import docet.DocetLanguage;
import docet.SimpleDocetDocumentAccessor;

public class DocetSampleDocumentAccessor extends SimpleDocetDocumentAccessor {

    @Override
    public String getPlaceholderForDocument(final DocetDocumentPlaceholder placeholder, final DocetLanguage lang) {
        final String res;
        switch (placeholder) {
            case PRODUCT_NAME:
                res = "Sample app";
                break;
            case PRODUCT_VERSION:
                res = "1.6.0";
                break;
            case PDF_COVER_SUBTITLE_1:
                res = "subtitle";
                break;
            case PDF_FOOTER_COVER:
                res = "This is a sample app - &copy; Copyright 2017";
                break;
            default:
                res = "";
        }
        return res;
    }
}
