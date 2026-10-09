package com.embabel.devoxx;

import com.embabel.agent.rag.ingestion.ContentChunker;
import com.embabel.agent.rag.ingestion.transform.AddTitlesChunkTransformer;
import com.embabel.agent.rag.lucene.LuceneSearchOperations;
import com.embabel.agent.rag.tools.ToolishRag;
import com.embabel.common.ai.model.DefaultModelSelectionCriteria;
import com.embabel.common.ai.model.ModelProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
class RagConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(RagConfiguration.class);

    static final Path CORPUS_DIR = Path.of("data", "sherlock");

    /**
     * Lucene store with both BM25 text search and vector search, persisted
     * on disk so embeddings are computed once. On later starts the existing
     * index and its chunks are loaded and ingestion is skipped for documents
     * already present. Delete the index directory to force re-ingestion.
     */
    @Bean
    LuceneSearchOperations luceneSearchOperations(
            ModelProvider modelProvider,
            @Value("${holmes.index-dir:data/index}") Path indexDir) {
        var embeddingService = modelProvider.getEmbeddingService(DefaultModelSelectionCriteria.INSTANCE);
        var store = LuceneSearchOperations
                .withName("holmes")
                .withEmbeddingService(embeddingService)
                .withChunkerConfig(new ContentChunker.Config(1200, 150, 100))
                // Prefix chunks with their section titles so the LLM can tell stories apart
                .withChunkTransformer(AddTitlesChunkTransformer.INSTANCE)
                .withIndexPath(indexDir)
                .buildAndLoadChunks();
        logger.info("Lucene index at {}: {}", indexDir.toAbsolutePath(), store.info());
        var count = new HolmesCorpus(store).ingest(CORPUS_DIR);
        logger.info("Ingested {} new document(s): {}", count, store.info());
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
                "The full text of the Sherlock Holmes canon: the four novels and all 56 short stories",
                luceneSearchOperations);
    }
}
