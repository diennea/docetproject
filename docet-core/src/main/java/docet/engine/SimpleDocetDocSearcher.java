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
package docet.engine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.fr.FrenchAnalyzer;
import org.apache.lucene.analysis.it.ItalianAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.highlight.Fragmenter;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.InvalidTokenOffsetsException;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.search.highlight.SimpleSpanFragmenter;
import org.apache.lucene.store.FSDirectory;

import docet.error.DocetDocumentSearchException;
import docet.model.DocetPackageDescriptor;
import docet.model.DocetPage;

/**
 * Simple implementation of a (Lucene-based) document searcher.
 *
 * @author matteo.casadei
 *
 */
public class SimpleDocetDocSearcher implements DocetDocumentSearcher {

    private static final int DEFAULT_MAX_TERMS_DISTANCE_IN_SEARCH = 6;
    private static final int DEFAULT_TERMS_MAX_DISTANCE_SIMILARITY = 1;
    private static final int MAX_NUM_FRAGMENTS = 3;
    private static final int MIN_TERM_LENGTH_THRESHOLD = 3;
    private static final String MACHING_EXCERPTS_SEPARATOR = " ... ";

    private static final String LUCENE_QUERY_CONTENT_PREFIX = "contents-";

    private final ReentrantLock lock;
    private final Path searchIndexPath;
    private IndexReader reader;
    private FSDirectory index;
    private DocetPackageDescriptor descriptor;

    public SimpleDocetDocSearcher(final Path searchIndexPath, final DocetPackageDescriptor descriptor) {
        this.searchIndexPath = searchIndexPath;
        this.descriptor = descriptor;
        this.lock  = new ReentrantLock(true);
    }

    private String getFallbackLangForLang(final String lang) {
        final String fallbackLang = this.descriptor.getFallbackLangForLang(lang);
        if (fallbackLang == null) {
            return "";
        } else {
            return fallbackLang;
        }
    }

    @Override
    public List<DocetPage> searchForMatchingDocuments(final String searchText, final String lang, final int maxNumResults)
        throws DocetDocumentSearchException {
        final List<DocetPage> results = new ArrayList<>();
        final String fallbackLang = this.getFallbackLangForLang(lang);
        final String actualSearchLang;
        if (fallbackLang.isEmpty()) {
            actualSearchLang = lang;
        } else {
            actualSearchLang = fallbackLang;
        }
        try {
            final IndexSearcher searcher = new IndexSearcher(reader);
            final Analyzer analyzer = new AnalyzerBuilder().language(actualSearchLang).build();
            QueryParser queryParser = new QueryParser(LUCENE_QUERY_CONTENT_PREFIX + actualSearchLang, analyzer);
            final Query query = queryParser.parse(constructLucenePhraseTermSearchQuery(searchText));
            final QueryScorer queryScorer = new QueryScorer(query, LUCENE_QUERY_CONTENT_PREFIX + actualSearchLang);

            final Fragmenter fragmenter = new SimpleSpanFragmenter(queryScorer);
            final Highlighter highlighter = new Highlighter(queryScorer);
            highlighter.setMaxDocCharsToAnalyze(Integer.MAX_VALUE);
            highlighter.setTextFragmenter(fragmenter);

            final TopDocs res =  searcher.search(query, maxNumResults);
            final float maxScore = res.getMaxScore();
            final List<ScoreDoc> scoreDocs = Arrays.asList(res.scoreDocs);
            Map<org.apache.lucene.document.Document, String> docs = new HashMap<>();
            Map<String, ScoreDoc> scoresForDocs = new HashMap<>();
            for (final ScoreDoc sd : scoreDocs) {
                final org.apache.lucene.document.Document doc = searcher.doc(sd.doc);
                final String contents = doc.get(LUCENE_QUERY_CONTENT_PREFIX + actualSearchLang);
                final String docId = doc.get("id");
                final String[] fragments = highlighter.getBestFragments(analyzer, LUCENE_QUERY_CONTENT_PREFIX
                    + actualSearchLang, contents, MAX_NUM_FRAGMENTS);
                List<String> fragmentList = Arrays.asList(fragments);
                fragmentList = fragmentList.stream().map(s1 -> s1.trim().split("\n"))
                        .map(s1 -> Arrays.asList(s1).stream().filter(s -> !s.trim().isEmpty())
                                .reduce((sa, sb) -> sa + MACHING_EXCERPTS_SEPARATOR + sb).orElse(MACHING_EXCERPTS_SEPARATOR))
                                .collect(Collectors.toList());
                docs.put(doc, MACHING_EXCERPTS_SEPARATOR  + fragmentList.stream()
                        .filter(s -> !s.isEmpty())
                        .reduce((s1, s2) -> s1 + "..." + s2).orElse("") + MACHING_EXCERPTS_SEPARATOR);
                scoresForDocs.putIfAbsent(docId, sd);
            }
            docs.entrySet().stream().forEach(e -> {
                final int relevance = Math.round((scoresForDocs.get(e.getKey().get("id")).score / maxScore) * 100);
                results.add(DocetPage.toDocetDocument(e.getKey(), e.getValue(), relevance));
            });
            return results;
        } catch (ParseException | IOException | InvalidTokenOffsetsException ex) {
            throw new DocetDocumentSearchException("Error on searching query " + searchText + " for lang "
                                                        + actualSearchLang, ex);
        }
    }

    @Override
    public boolean open() throws IOException {
        final boolean res;
        try {
            this.lock.lock();
            if (!isOpen()) {
                this.index = FSDirectory.open(searchIndexPath);
                this.reader = DirectoryReader.open(this.index);
                res = true;
            } else {
                res = false;
            }
        } finally {
            this.lock.unlock();
        }
        return res;
    }

    @Override
    public boolean close() throws IOException {
        final boolean res;
        try {
            this.lock.lock();
            if (isOpen()) {
                if (this.reader != null) {
                    this.reader.close();
                    this.reader = null;
                }
                if (this.index != null) {
                    this.index.close();
                    this.index = null;
                }
                res = true;
            } else {
                res = false;
            }
        } finally {
            this.lock.unlock();
        }
        return res;
    }

    private boolean isOpen() {
        return this.reader != null && this.index != null;
    }

    private String constructLucenePhraseTermSearchQuery(final String searchText) {
        final String phraseSearch = "\"" + searchText + "\"~" + DEFAULT_MAX_TERMS_DISTANCE_IN_SEARCH;
        final List<String> singleTerms = Arrays.asList(searchText.split("\\s")).stream()
                .filter(s -> !s.trim().isEmpty() && s.trim().length() > MIN_TERM_LENGTH_THRESHOLD)
                .map(s -> s.trim() + "~" + DEFAULT_TERMS_MAX_DISTANCE_SIMILARITY)
                .collect(Collectors.toList());
        final String singleTermsQuery = singleTerms.stream()
                .reduce("", (t1, t2) -> t1 + " OR " + t2);
        return phraseSearch + singleTermsQuery;
    }

    /**
     *
     */
    private static class AnalyzerBuilder {

        private String lang;
        private static final String DEFAULT_LANGUAGE = "it";

        public AnalyzerBuilder() {
            this.lang = DEFAULT_LANGUAGE;
        }

        public AnalyzerBuilder language(final String lang) {
            this.lang = lang;
            return this;
        }

        public Analyzer build() {
            final Analyzer analyzer;
            switch (this.lang) {
                case "fr":
                    analyzer = new FrenchAnalyzer();
                    break;
                case "it":
                    analyzer = new ItalianAnalyzer();
                    break;
                case "en":
                default:
                    analyzer = new StandardAnalyzer();
            }
            return analyzer;
        }
    }
}
