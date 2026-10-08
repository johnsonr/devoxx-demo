package com.embabel.devoxx;

import com.embabel.agent.rag.ingestion.ContentChunker;
import com.embabel.agent.rag.ingestion.transform.AddTitlesChunkTransformer;
import com.embabel.agent.rag.lucene.LuceneSearchOperations;
import com.embabel.agent.rag.tools.ToolishRag;
import com.embabel.common.ai.model.DefaultModelSelectionCriteria;
import com.embabel.common.ai.model.ModelProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
class RagConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(RagConfiguration.class);

    static final Path CORPUS_DIR = Path.of("data", "sherlock");

    /**
     * In-memory Lucene store with both BM25 text search and vector search.
     * No index path is set, so nothing touches disk. The corpus is ingested
     * here so the store is populated before any shell command can run.
     */
    @Bean
    LuceneSearchOperations luceneSearchOperations(ModelProvider modelProvider) {
        var embeddingService = modelProvider.getEmbeddingService(DefaultModelSelectionCriteria.INSTANCE);
        var store = LuceneSearchOperations
                .withName("holmes")
                .withEmbeddingService(embeddingService)
                .withChunkerConfig(new ContentChunker.Config(1200, 150, 100))
                // Prefix chunks with their section titles so the LLM can tell stories apart
                .withChunkTransformer(AddTitlesChunkTransformer.INSTANCE)
                .build();
        var count = new HolmesCorpus(store).ingest(CORPUS_DIR);
        logger.info("Ingested {} document(s): {}", count, store.info());
        return store;
    }

    /**
     * Exposes the store to LLMs as tools: vectorSearch, textSearch, regexSearch,
     * broadenChunk, zoomOut and readSection. The LLM drives the retrieval.
     */
    @Bean
    ToolishRag holmesRag(LuceneSearchOperations luceneSearchOperations) {
        return new ToolishRag(
                "holmes",
                "The full text of the twelve stories in The Adventures of Sherlock Holmes",
                luceneSearchOperations);
    }
}
