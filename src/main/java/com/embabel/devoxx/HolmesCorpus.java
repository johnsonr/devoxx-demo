package com.embabel.devoxx;

import com.embabel.agent.rag.ingestion.TikaHierarchicalContentReader;
import com.embabel.agent.rag.ingestion.policy.NeverRefreshExistingDocumentContentPolicy;
import com.embabel.agent.rag.lucene.LuceneSearchOperations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Ingests the Sherlock Holmes stories into the Lucene store.
 * Tika parses the Markdown into a document hierarchy (book, story, paragraphs),
 * which is then chunked and embedded.
 */
public class HolmesCorpus {

    private static final Logger logger = LoggerFactory.getLogger(HolmesCorpus.class);

    private final LuceneSearchOperations store;

    public HolmesCorpus(LuceneSearchOperations store) {
        this.store = store;
    }

    /**
     * Ingest every file in the directory. Returns the number of documents ingested.
     */
    public int ingest(Path directory) {
        try (Stream<Path> files = Files.list(directory)) {
            return (int) files
                    .filter(Files::isRegularFile)
                    .map(this::ingestFile)
                    .filter(ingested -> ingested)
                    .count();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read corpus directory " + directory, e);
        }
    }

    private boolean ingestFile(Path file) {
        var uri = file.toAbsolutePath().toUri().toString();
        var document = NeverRefreshExistingDocumentContentPolicy.INSTANCE
                .ingestUriIfNeeded(store, new TikaHierarchicalContentReader(), uri);
        if (document == null) {
            logger.info("Already ingested {}", file.getFileName());
            return false;
        }
        logger.info("Ingested {} as document {}", file.getFileName(), document.getId());
        return true;
    }
}
