package com.embabel.devoxx;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.Ai;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.domain.library.HasContent;
import com.embabel.agent.rag.tools.ToolishRag;
import org.springframework.lang.NonNull;

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
                    conclusion,
                    reasoning,
                    String.join("\n\n", evidence.stream().map(e -> "> " + e).toList())
            ).trim();
        }
    }

    private final ToolishRag holmesRag;

    CaseAgent(ToolishRag holmesRag) {
        this.holmesRag = holmesRag;
    }

    @Action
    Evidence gatherEvidence(UserInput question, Ai ai) {
        return ReferenceTools.withReferenceOnce(ai.withDefaultLlm(), holmesRag)
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
