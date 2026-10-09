package com.embabel.devoxx;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.Ai;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.domain.library.HasContent;
import com.embabel.agent.rag.tools.ToolishRag;
import org.springframework.lang.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * A three-step investigation planned by GOAP.
 * The planner works backwards from the goal (a CaseFile): to write a CaseFile it
 * needs a Deduction, to deduce it needs Evidence, and Evidence comes from the
 * user's question plus agentic RAG over the stories.
 */
@Agent(description = "Investigate a question about the Sherlock Holmes stories: "
        + "gather textual evidence, reason about it, and write up a case file")
public class CaseAgent {

    public record Evidence(List<String> passages) {
    }

    public record Deduction(String reasoning, String conclusion) {
    }

    public record CaseFile(
            String question,
            String conclusion,
            String reasoning,
            List<String> evidence
    ) implements HasContent {

        /** Column at which the shell output wraps; LLM prose arrives as one long line per paragraph. */
        static final int WIDTH = 100;

        @Override
        @NonNull
        public String getContent() {
            return """
                    # Case file: %s

                    ## Conclusion
                    %s

                    ## Reasoning
                    %s

                    ## Evidence
                    %s
                    """.formatted(
                    question,
                    wrap(conclusion, ""),
                    wrap(reasoning, ""),
                    String.join("\n\n", evidence.stream().map(e -> wrap(e, "> ")).toList())
            ).trim();
        }

        /**
         * Wraps each paragraph of the text at {@link #WIDTH} columns, prefixing every
         * line with the given prefix. Existing line breaks are kept.
         */
        static String wrap(String text, String prefix) {
            var lines = new ArrayList<String>();
            for (var paragraph : text.strip().split("\\R")) {
                var line = new StringBuilder(prefix);
                for (var word : paragraph.strip().split("\\s+")) {
                    if (line.length() > prefix.length() && line.length() + 1 + word.length() > WIDTH) {
                        lines.add(line.toString());
                        line = new StringBuilder(prefix);
                    }
                    if (line.length() > prefix.length()) {
                        line.append(' ');
                    }
                    line.append(word);
                }
                lines.add(line.toString());
            }
            return String.join("\n", lines);
        }
    }

    private final ToolishRag holmesRag;

    CaseAgent(ToolishRag holmesRag) {
        this.holmesRag = holmesRag;
    }

    @Action
    Evidence gatherEvidence(UserInput question, Ai ai) {
        return ai
                .withDefaultLlm()
                .withReference(holmesRag)
                .creating(Evidence.class)
                .fromPrompt("""
                        You are Dr Watson assisting Sherlock Holmes.
                        Search the stories for passages that bear on the question below.
                        Use the search tools: try vector search and text search, and zoom out
                        from promising chunks to read their full context.
                        Return 3 to 6 short verbatim passages, each prefixed with the story title.

                        # Question
                        %s
                        """.formatted(question.getContent()).trim());
    }

    @Action
    Deduction deduce(UserInput question, Evidence evidence, Ai ai) {
        return ai
                .withDefaultLlm()
                .creating(Deduction.class)
                .fromPrompt("""
                        You are Sherlock Holmes. Reason from the evidence alone to answer the question.
                        If the evidence is insufficient, say so in the conclusion.

                        # Question
                        %s

                        # Evidence
                        %s
                        """.formatted(
                        question.getContent(),
                        String.join("\n\n", evidence.passages())
                ).trim());
    }

    @AchievesGoal(description = "A case file answering the user's question has been written")
    @Action
    CaseFile writeCaseFile(UserInput question, Evidence evidence, Deduction deduction) {
        // Plain code: no LLM needed once the typed inputs exist
        return new CaseFile(
                question.getContent(),
                deduction.conclusion(),
                deduction.reasoning(),
                evidence.passages());
    }
}
