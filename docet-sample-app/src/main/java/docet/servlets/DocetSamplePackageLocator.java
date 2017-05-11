package docet.servlets;

import java.io.IOException;

import docet.SimplePackageLocator;
import docet.engine.DocetConfiguration;

public class DocetSamplePackageLocator extends SimplePackageLocator {
    
    public DocetSamplePackageLocator(final DocetConfiguration docetConf) throws IOException {
        super(docetConf);
    }
}
