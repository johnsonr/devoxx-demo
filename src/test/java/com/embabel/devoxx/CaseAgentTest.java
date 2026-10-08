package com.embabel.devoxx;

import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.rag.lucene.LuceneSearchOperations;
import com.embabel.agent.rag.tools.ToolishRag;
import com.embabel.agent.test.unit.FakeOperationContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CaseAgentTest {

    private final ToolishRag rag = new ToolishRag(
            "holmes", "Test stories", LuceneSearchOperations.withName("test").build());
    private final CaseAgent agent = new CaseAgent(rag);
    private final UserInput question = new UserInput("Who is Irene Adler?");

    @Test
    void gatherEvidencePromptsWithTheQuestion() {
        var context = FakeOperationContext.create();
        context.expectResponse(new CaseAgent.Evidence(List.of("To Sherlock Holmes she is always the woman.")));

        var evidence = agent.gatherEvidence(question, context.ai());

        assertEquals(1, evidence.passages().size());
        var prompt = context.getLlmInvocations().getFirst().getMessages().getFirst().getContent();
        assertTrue(prompt.contains("Irene Adler"), prompt);
    }

    @Test
    void deducePromptsWithQuestionAndEvidence() {
        var context = FakeOperationContext.create();
        context.expectResponse(new CaseAgent.Deduction("She outwitted Holmes.", "The woman."));
        var evidence = new CaseAgent.Evidence(List.of("To Sherlock Holmes she is always the woman."));

        var deduction = agent.deduce(question, evidence, context.ai());

        assertEquals("The woman.", deduction.conclusion());
        var prompt = context.getLlmInvocations().getFirst().getMessages().getFirst().getContent();
        assertTrue(prompt.contains("Irene Adler"), prompt);
        assertTrue(prompt.contains("always the woman"), prompt);
    }

    @Test
    void caseFileNeedsNoLlm() {
        var evidence = new CaseAgent.Evidence(List.of("passage one"));
        var deduction = new CaseAgent.Deduction("because", "therefore");

        var caseFile = agent.writeCaseFile(question, evidence, deduction);

        var content = caseFile.getContent();
        assertTrue(content.contains("Irene Adler"));
        assertTrue(content.contains("therefore"));
        assertTrue(content.contains("> passage one"));
    }
}
