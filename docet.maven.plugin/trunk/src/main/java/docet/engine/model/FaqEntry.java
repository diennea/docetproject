package docet.engine.model;

import java.nio.file.Path;

public class FaqEntry {
    private final String title;
    private final Path faqPath;

    public FaqEntry(final Path faqPath, final String title) {
        this.title = title;
        this.faqPath = faqPath;
    }

    public String getTitle() {
        return title;
    }

    public Path getFaqPath() {
        return faqPath;
    }
}
