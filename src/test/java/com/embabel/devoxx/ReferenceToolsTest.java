package com.embabel.devoxx;

import com.embabel.agent.api.reference.LlmReference;
import com.embabel.agent.rag.lucene.LuceneSearchOperations;
import com.embabel.agent.rag.tools.ToolishRag;
import com.embabel.agent.skills.Skills;
import com.embabel.agent.skills.script.DockerSkillScriptExecutionEngine;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReferenceToolsTest {

    @Test
    void ragToolsCarryThePrefixOnce() {
        try (var store = LuceneSearchOperations.withName("holmes-test").build()) {
            assertSinglyPrefixed(new ToolishRag("holmes", "test", store));
        }
    }

    @Test
    void skillToolsCarryThePrefixOnce() {
        var skills = new Skills("holmes-skills", "test")
                .withLocalSkill(SkillsConfiguration.SKILL_DIR)
                .withScriptExecutionEngine(new DockerSkillScriptExecutionEngine());
        assertSinglyPrefixed(skills);
    }

    private static void assertSinglyPrefixed(LlmReference reference) {
        var prefix = reference.toolPrefix() + "_";
        List<String> names = ReferenceTools.toolsOf(reference).stream()
                .map(t -> t.getDefinition().getName())
                .toList();

        assertFalse(names.isEmpty());
        assertEquals(reference.tools().size(), names.size(), "No tools gained or lost: " + names);
        assertEquals(names.size(), new HashSet<>(names).size(), "Duplicate tool names: " + names);
        for (var name : names) {
            assertTrue(name.startsWith(prefix), name + " should start with " + prefix);
            assertFalse(name.startsWith(prefix + prefix), name + " is double-prefixed");
        }
    }
}
