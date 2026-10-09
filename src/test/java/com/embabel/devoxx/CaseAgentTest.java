package com.embabel.devoxx;

import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.rag.lucene.LuceneSearchOperations;
import com.embabel.agent.rag.tools.ToolishRag;
import com.embabel.agent.test.unit.FakeOperationContext;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CaseAgentTest {

    private final ToolishRag rag = new ToolishRag(
            "holmes", "Test stories", LuceneSearchOperations.withName("test").build());
    private final CaseAgent agent = new CaseAgent(rag);
    private final UserInput question = new UserInput("Who is Irene Adler?");
    private final CaseAgent.Evidence evidence = new CaseAgent.Evidence(List.of("To Sherlock Holmes she is always the woman."));
    private final CaseAgent.Deduction deduction = new CaseAgent.Deduction("She outwitted Holmes.", "The woman.");

    @Test
    void gatherEvidencePromptsWithTheQuestionAndRegistersEachSearchToolOnce() {
        var context = FakeOperationContext.create();
        context.expectResponse(new CaseAgent.Evidence(List.of("To Sherlock Holmes she is always the woman.")));

        var result = agent.gatherEvidence(question, context.ai());

        assertEquals(1, result.passages().size());
        var invocation = context.getLlmInvocations().getFirst();
        assertTrue(invocation.getMessages().getFirst().getContent().contains("Irene Adler"));

        // Embabel 1.5.3 registered every reference tool twice; 1.5.4 registers each once, prefixed once
        var names = invocation.getInteraction().getTools().stream().map(t -> t.getDefinition().getName()).toList();
        assertFalse(names.isEmpty());
        assertEquals(names.size(), new HashSet<>(names).size(), "Duplicate tool names: " + names);
        for (var name : names) {
            assertTrue(name.startsWith("holmes_") && !name.startsWith("holmes_holmes_"), name);
        }
    }

    @Test
    void deducePromptsWithQuestionAndEvidence() {
        var context = FakeOperationContext.create();
        context.expectResponse(new CaseAgent.Deduction("She outwitted Holmes.", "The woman."));

        var result = agent.deduce(question, evidence, context.ai());

        assertEquals("The woman.", result.conclusion());
        var prompt = context.getLlmInvocations().getFirst().getMessages().getFirst().getContent();
        assertTrue(prompt.contains("Irene Adler"), prompt);
        assertTrue(prompt.contains("always the woman"), prompt);
    }

    @Test
    void caseFileWrapsLongProseAndKeepsQuotePrefixes() {
        var longSentence = "word ".repeat(60).strip();
        var caseFile = agent.writeCaseFile(question,
                new CaseAgent.Evidence(List.of(longSentence)),
                new CaseAgent.Deduction(longSentence, longSentence));

        var content = caseFile.getContent();
        for (var line : content.split("\n")) {
            assertTrue(line.length() <= CaseAgent.CaseFile.WIDTH, "Line too long: " + line);
        }
        var quoted = content.lines().filter(l -> l.startsWith("> ")).count();
        assertTrue(quoted >= 3, "Every continuation line of a passage keeps the quote prefix: " + content);
        assertEquals(60, content.lines().filter(l -> l.startsWith("> ")).mapToInt(l -> l.split(" ").length - 1).sum());
    }

    @Test
    void caseFileNeedsNoLlm() {
        var caseFile = agent.writeCaseFile(question, evidence, deduction);

        var content = caseFile.getContent();
        assertTrue(content.contains("Irene Adler"));
        assertTrue(content.contains("The woman."));
        assertTrue(content.contains("> To Sherlock Holmes"));
    }
}
