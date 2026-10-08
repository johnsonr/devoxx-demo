package com.embabel.devoxx;

import com.embabel.agent.sandbox.ScratchTool;
import com.embabel.agent.sandbox.docker.DockerSandboxSessionManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HolmesScriptRunnerTest {

    private static final DockerSandboxSessionManager sessions = new DockerSandboxSessionManager();
    private static final ScratchTool scratch = new ScratchTool(sessions);
    private final List<ScriptRunListener.ScriptRun> runs = new ArrayList<>();
    private final HolmesScriptRunner runner = new HolmesScriptRunner(scratch, Path.of("data", "sherlock"), runs::add);

    @AfterAll
    static void closeSandbox() {
        scratch.close();
        sessions.close();
    }

    @Test
    void exposesRunScriptWithLanguageAndCode() {
        var tools = runner.tools();
        assertEquals(1, tools.size(), tools.toString());
        var definition = tools.get(0).getDefinition();
        assertEquals("run_script", definition.getName());
        var schema = definition.getInputSchema().toJsonSchema();
        assertTrue(schema.contains("\"language\""), schema);
        assertTrue(schema.contains("\"code\""), schema);
    }

    @Test
    void rejectsUnknownLanguage() {
        assertTrue(runner.runScript("cobol", "DISPLAY 'hi'.").startsWith("Unsupported language"));
    }

    /**
     * Runs real scripts in the sandbox container. Needs Docker and the sandbox image.
     */
    @Test
    @EnabledIf("sandboxImageAvailable")
    void runsPythonAgainstTheStagedCorpus() {
        var output = runner.runScript("python", """
                import glob, re
                stories = 0
                for path in sorted(glob.glob("corpus/*.md")):
                    text = open(path, encoding="utf-8").read()
                    stories += len(re.findall(r"^## ", text, flags=re.M))
                print("files", len(glob.glob("corpus/*.md")))
                print("headings", stories)
                """);
        assertTrue(output.contains("files 5"), output);
        assertTrue(output.contains("headings 58"), output);

        var listing = runner.runScript("bash", "ls corpus | wc -l");
        assertTrue(listing.trim().endsWith("5"), listing);

        assertEquals(2, runs.size(), "Listener sees every run: " + runs);
        assertEquals("bash", runs.get(1).language());
        assertEquals("ls corpus | wc -l", runs.get(1).code());
        assertEquals(listing, runs.get(1).output());
        assertFalse(runs.get(1).duration().isNegative());
    }

    static boolean sandboxImageAvailable() {
        return HolmesSkillsTest.sandboxImageAvailable();
    }
}
