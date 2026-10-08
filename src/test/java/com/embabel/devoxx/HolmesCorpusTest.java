package com.embabel.devoxx;

import com.embabel.agent.rag.lucene.LuceneSearchOperations;
import com.embabel.agent.rag.model.Chunk;
import com.embabel.agent.rag.service.RagRequest;
import com.embabel.agent.rag.tools.ToolishRag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises ingestion and search with no LLM and no embedding model:
 * an in-memory Lucene store still supports BM25 text search.
 */
class HolmesCorpusTest {

    private LuceneSearchOperations store;

    @BeforeEach
    void ingest() {
        store = LuceneSearchOperations.withName("holmes-test").build();
        var ingested = new HolmesCorpus(store).ingest(Path.of("data", "sherlock"));
        assertEquals(1, ingested);
    }

    @AfterEach
    void close() {
        store.close();
    }

    @Test
    void chunksTheWholeBook() {
        assertTrue(store.info().getChunkCount() > 100, "Expected many chunks, got " + store.info());
    }

    @Test
    void textSearchFindsTheRedHeadedLeague() {
        var request = RagRequest.query("Red-Headed League").withSimilarityThreshold(0.0).withTopK(5);
        var results = store.textSearch(request, Chunk.class);
        assertFalse(results.isEmpty());
        assertTrue(results.stream().anyMatch(r -> r.getMatch().getText().contains("Red-Headed League")));
    }

    @Test
    void toolishRagExposesSearchTools() {
        var rag = new ToolishRag("holmes", "Sherlock Holmes stories", store);
        var toolNames = rag.tools().stream().map(t -> t.getDefinition().getName()).toList();
        assertTrue(toolNames.stream().anyMatch(n -> n.endsWith("textSearch")), toolNames.toString());
        assertTrue(toolNames.stream().anyMatch(n -> n.endsWith("vectorSearch")), toolNames.toString());
        assertTrue(toolNames.stream().anyMatch(n -> n.endsWith("zoomOut")), toolNames.toString());
    }
}
