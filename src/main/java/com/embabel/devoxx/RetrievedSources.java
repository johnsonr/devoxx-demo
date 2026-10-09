package com.embabel.devoxx;

import com.embabel.agent.rag.model.Chunk;
import com.embabel.agent.rag.model.ContentRoot;
import com.embabel.agent.rag.model.Retrievable;
import com.embabel.agent.rag.model.Section;
import com.embabel.agent.rag.tools.ResultsEvent;
import com.embabel.agent.rag.tools.ResultsListener;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Collects the sources the LLM retrieved during one chat turn.
 * Attach a fresh instance per turn with {@code holmesRag.withListener(sources)}:
 * every search tool call then reports its results here. Afterwards
 * {@link #summary(String)} lists the stories, most relevant first, and marks
 * the ones whose text the answer actually quotes.
 */
final class RetrievedSources implements ResultsListener {

    /** A run of this many consecutive words shared with the answer counts as a quotation. */
    static final int QUOTE_WORDS = 6;

    private static final int MAX_LISTED = 10;

    private static final class Source {
        double bestScore;
        final List<String> texts = new ArrayList<>();
    }

    /** One line of the summary: a story, and the passage the answer quoted from it, if any. */
    record Citation(String label, String quoted) {
        boolean isQuoted() {
            return quoted != null;
        }
    }

    private final Map<String, Source> byLabel = new LinkedHashMap<>();

    @Override
    public synchronized void onResultsEvent(ResultsEvent event) {
        for (var result : event.getResults()) {
            var match = result.getMatch();
            var source = byLabel.computeIfAbsent(labelFor(match), k -> new Source());
            source.bestScore = Math.max(source.bestScore, result.getScore());
            if (match instanceof Chunk chunk) {
                source.texts.add(chunk.getText());
            }
        }
    }

    synchronized boolean isEmpty() {
        return byLabel.isEmpty();
    }

    /** The texts of the chunks retrieved for a story, for tests and debugging. */
    synchronized List<String> textsFor(String label) {
        var source = byLabel.get(label);
        return source == null ? List.of() : List.copyOf(source.texts);
    }

    /**
     * Story labels, most relevant first.
     */
    synchronized List<String> labels() {
        return byLabel.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.comparingDouble((Source s) -> s.bestScore).reversed()))
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * Each retrieved story with the passage the answer quotes from it, quoted
     * stories first, then by relevance.
     */
    synchronized List<Citation> citations(String answer) {
        var answerWords = words(answer);
        var citations = new ArrayList<Citation>();
        for (var label : labels()) {
            String quoted = null;
            for (var text : byLabel.get(label).texts) {
                var run = longestCommonRun(answerWords, words(text));
                if (run.size() >= QUOTE_WORDS && (quoted == null || run.size() > words(quoted).size())) {
                    quoted = String.join(" ", run);
                }
            }
            citations.add(new Citation(label, quoted));
        }
        citations.sort(Comparator.comparing((Citation c) -> !c.isQuoted()));
        return citations;
    }

    /**
     * A bullet list of the sources, quoted ones first with the quoted passage.
     */
    String summary(String answer) {
        var citations = citations(answer);
        var sb = new StringBuilder("Sources:");
        int shown = 0;
        for (var citation : citations) {
            if (shown == MAX_LISTED && !citation.isQuoted()) {
                break;
            }
            sb.append("\n• ").append(citation.label());
            if (citation.isQuoted()) {
                sb.append(" — quoted: “").append(citation.quoted()).append("”");
            }
            shown++;
        }
        if (shown < citations.size()) {
            sb.append("\n• … and ").append(citations.size() - shown).append(" more");
        }
        return sb.toString();
    }

    static String labelFor(Retrievable match) {
        if (match instanceof Chunk chunk) {
            var structure = chunk.getStructure();
            var story = structure.getLeafSectionTitle() != null
                    ? structure.getLeafSectionTitle()
                    : structure.getContainerSectionTitle();
            var book = structure.getRootDocumentTitle();
            if (story == null) {
                return book != null ? book : chunk.getId();
            }
            return book != null && !book.equals(story) ? story + " (" + book + ")" : story;
        }
        if (match instanceof Section section) {
            return section.getTitle();
        }
        if (match instanceof ContentRoot root) {
            return root.getTitle();
        }
        return match.getId();
    }

    /** Lower-case words with punctuation and quote marks stripped, so quoting survives reformatting. */
    static List<String> words(String text) {
        var result = new ArrayList<String>();
        for (var token : text.toLowerCase(Locale.ROOT).split("\\s+")) {
            var word = token.replaceAll("[^\\p{L}\\p{N}]", "");
            if (!word.isEmpty()) {
                result.add(word);
            }
        }
        return result;
    }

    /** The longest run of consecutive words that appears in both lists. */
    static List<String> longestCommonRun(List<String> a, List<String> b) {
        int bestLength = 0, bestEnd = 0;
        var previous = new int[b.size() + 1];
        for (int i = 1; i <= a.size(); i++) {
            var current = new int[b.size() + 1];
            for (int j = 1; j <= b.size(); j++) {
                if (a.get(i - 1).equals(b.get(j - 1))) {
                    current[j] = previous[j - 1] + 1;
                    if (current[j] > bestLength) {
                        bestLength = current[j];
                        bestEnd = i;
                    }
                }
            }
            previous = current;
        }
        return a.subList(bestEnd - bestLength, bestEnd);
    }
}
