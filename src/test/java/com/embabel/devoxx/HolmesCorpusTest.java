package com.embabel.devoxx;

import com.embabel.agent.rag.lucene.LuceneSearchOperations;
import com.embabel.agent.rag.model.Chunk;
import com.embabel.agent.rag.service.RagRequest;
import com.embabel.agent.rag.tools.ToolishRag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.io.TempDir;

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
        assertEquals(5, ingested);
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
    void reopeningOnDiskIndexSkipsIngestion(@TempDir Path indexDir) {
        var corpus = Path.of("data", "sherlock");
        var first = LuceneSearchOperations.withName("holmes-disk").withIndexPath(indexDir).build();
        assertEquals(5, new HolmesCorpus(first).ingest(corpus));
        var chunks = first.info().getChunkCount();
        first.close();

        var second = LuceneSearchOperations.withName("holmes-disk").withIndexPath(indexDir).buildAndLoadChunks();
        try {
            assertEquals(0, new HolmesCorpus(second).ingest(corpus), "Document should already be in the index");
            assertEquals(chunks, second.info().getChunkCount());
            var results = second.textSearch(RagRequest.query("Red-Headed League").withSimilarityThreshold(0.0).withTopK(5), Chunk.class);
            assertFalse(results.isEmpty());
        } finally {
            second.close();
        }
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
