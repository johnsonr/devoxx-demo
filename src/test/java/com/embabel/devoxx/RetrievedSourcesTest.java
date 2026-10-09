package com.embabel.devoxx;

import com.embabel.agent.rag.ingestion.TikaHierarchicalContentReader;
import com.embabel.agent.rag.ingestion.policy.NeverRefreshExistingDocumentContentPolicy;
import com.embabel.agent.rag.lucene.LuceneSearchOperations;
import com.embabel.agent.rag.model.Chunk;
import com.embabel.agent.rag.service.RagRequest;
import com.embabel.agent.rag.tools.ToolishRag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RetrievedSourcesTest {

    @Test
    void listenerSeesTheStoriesTheLlmSearchedAndMarksWhatTheAnswerQuotes() {
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
            assertEquals("The Adventure of the Devil’s Foot (His Last Bow)", sources.labels().get(0), sources.labels().toString());

            // An answer that quotes a retrieved passage is marked as such, reformatting and all
            var topChunk = store.textSearch(
                    RagRequest.query("Mortimer Tregennis").withSimilarityThreshold(0.0).withTopK(1), Chunk.class)
                    .get(0).getMatch().getText();
            var passage = String.join(" ", RetrievedSources.words(topChunk).subList(10, 18));
            var answer = "As the story puts it, \"" + passage.toUpperCase() + "\" and so on.";
            var summary = sources.summary(answer);
            assertTrue(summary.startsWith("Sources:\n• The Adventure of the Devil’s Foot (His Last Bow) — quoted: “" + passage), summary);

            // An answer that quotes nothing lists the stories without a quotation
            var plain = sources.summary("Nothing in the stories covers that.");
            assertEquals("Sources:\n• The Adventure of the Devil’s Foot (His Last Bow)", plain);
        }
    }

    @Test
    void longestCommonRunIgnoresPunctuationAndCase() {
        var a = RetrievedSources.words("He said, “Come into the arbour here and let us talk.”");
        var b = RetrievedSources.words("come into the arbour here and let us");
        assertEquals(List.of("come", "into", "the", "arbour", "here", "and", "let", "us"), RetrievedSources.longestCommonRun(a, b));
        assertTrue(RetrievedSources.longestCommonRun(a, RetrievedSources.words("nothing shared")).isEmpty());
    }
}
