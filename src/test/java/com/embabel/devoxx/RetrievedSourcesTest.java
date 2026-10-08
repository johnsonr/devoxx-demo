package com.embabel.devoxx;

import com.embabel.agent.rag.ingestion.TikaHierarchicalContentReader;
import com.embabel.agent.rag.ingestion.policy.NeverRefreshExistingDocumentContentPolicy;
import com.embabel.agent.rag.lucene.LuceneSearchOperations;
import com.embabel.agent.rag.tools.ToolishRag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class RetrievedSourcesTest {

    @Test
    void listenerSeesTheStoriesTheLlmSearched() {
        try (var store = LuceneSearchOperations.withName("sources-test").build()) {
            var uri = Path.of("data", "sherlock", "his-last-bow.md").toAbsolutePath().toUri().toString();
            NeverRefreshExistingDocumentContentPolicy.INSTANCE
                    .ingestUriIfNeeded(store, new TikaHierarchicalContentReader(), uri);

            var sources = new RetrievedSources();
            var rag = new ToolishRag("holmes", "test", store).withListener(sources);
            assertTrue(sources.isEmpty());

            var textSearch = rag.tools().stream()
                    .filter(t -> t.getDefinition().getName().endsWith("textSearch"))
                    .findFirst()
                    .orElseThrow();
            textSearch.call("""
                    {"query": "Mortimer Tregennis", "topK": 5, "similarityThreshold": 0.0}
                    """);

            assertFalse(sources.isEmpty());
            var labels = sources.labels();
            assertEquals("The Adventure of the Devil’s Foot (His last bow)", labels.get(0), labels.toString());
            assertTrue(sources.summary().startsWith("Sources consulted: The Adventure of the Devil’s Foot"), sources.summary());
        }
    }
}
