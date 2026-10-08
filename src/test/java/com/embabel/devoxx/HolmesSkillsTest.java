package com.embabel.devoxx;

import com.embabel.agent.api.tool.Tool;
import com.embabel.agent.skills.Skills;
import com.embabel.agent.skills.script.DockerSkillScriptExecutionEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HolmesSkillsTest {

    private final Skills skills = new Skills("holmes-skills", "test")
            .withLocalSkill(SkillsConfiguration.SKILL_DIR)
            .withScriptExecutionEngine(new DockerSkillScriptExecutionEngine());

    @Test
    void pythonScriptIsExposedAsTool() {
        var toolNames = skills.tools().stream().map(t -> t.getDefinition().getName()).toList();

        assertTrue(toolNames.stream().anyMatch(n -> n.endsWith("mentions")), toolNames.toString());
    }

    /**
     * Runs the Python script in the sandbox container. Needs Docker and the sandbox image.
     */
    @Test
    @EnabledIf("sandboxImageAvailable")
    void scriptRunsInDockerSandbox() {
        var mentions = skills.tools().stream()
                .filter(t -> t.getDefinition().getName().endsWith("mentions"))
                .findFirst()
                .orElseThrow();

        var result = mentions.call("""
                {"args": ["Watson"], "inputFiles": ["data/sherlock/adventures-of-sherlock-holmes.md"]}
                """);

        var text = assertInstanceOf(Tool.Result.Text.class, result, result.toString());
        assertTrue(text.getContent().contains("II. THE RED-HEADED LEAGUE | 10"), text.getContent());
        assertTrue(text.getContent().contains("TOTAL | "), text.getContent());
    }

    static boolean sandboxImageAvailable() {
        try {
            var process = new ProcessBuilder("docker", "image", "inspect", DockerSkillScriptExecutionEngine.DEFAULT_IMAGE)
                    .redirectErrorStream(true)
                    .start();
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
