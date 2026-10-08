package com.embabel.devoxx;

import com.embabel.agent.rag.model.Chunk;
import com.embabel.agent.rag.model.ContentRoot;
import com.embabel.agent.rag.model.Retrievable;
import com.embabel.agent.rag.model.Section;
import com.embabel.agent.rag.tools.ResultsEvent;
import com.embabel.agent.rag.tools.ResultsListener;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects the sources the LLM retrieved during one chat turn.
 * Attach a fresh instance per turn with {@code holmesRag.withListener(sources)}:
 * every search tool call then reports its results here, and the best score
 * per story is kept so the stories can be listed most relevant first.
 */
final class RetrievedSources implements ResultsListener {

    private static final String GUTENBERG_PREFIX = "The Project Gutenberg eBook of ";

    private final Map<String, Double> bestScoreByLabel = new LinkedHashMap<>();

    @Override
    public synchronized void onResultsEvent(ResultsEvent event) {
        for (var result : event.getResults()) {
            var label = labelFor(result.getMatch());
            bestScoreByLabel.merge(label, result.getScore(), Math::max);
        }
    }

    synchronized boolean isEmpty() {
        return bestScoreByLabel.isEmpty();
    }

    /**
     * Story labels, most relevant first.
     */
    synchronized List<String> labels() {
        return bestScoreByLabel.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder()))
                .map(Map.Entry::getKey)
                .toList();
    }

    String summary() {
        return "Sources consulted: " + String.join("; ", labels());
    }

    static String labelFor(Retrievable match) {
        if (match instanceof Chunk chunk) {
            var structure = chunk.getStructure();
            var story = structure.getLeafSectionTitle() != null
                    ? structure.getLeafSectionTitle()
                    : structure.getContainerSectionTitle();
            var book = stripGutenberg(structure.getRootDocumentTitle());
            if (story == null) {
                return book != null ? book : chunk.getId();
            }
            return book != null && !book.equals(story) ? story + " (" + book + ")" : story;
        }
        if (match instanceof Section section) {
            return section.getTitle();
        }
        if (match instanceof ContentRoot root) {
            return stripGutenberg(root.getTitle());
        }
        return match.getId();
    }

    private static String stripGutenberg(String title) {
        if (title == null) {
            return null;
        }
        return title.startsWith(GUTENBERG_PREFIX) ? title.substring(GUTENBERG_PREFIX.length()) : title;
    }
}
