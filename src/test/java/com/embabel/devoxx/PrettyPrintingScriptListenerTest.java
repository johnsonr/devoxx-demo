package com.embabel.devoxx;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class PrettyPrintingScriptListenerTest {

    @Test
    void printsNumberedSourceAndOutput() {
        var bytes = new ByteArrayOutputStream();
        var listener = new PrettyPrintingScriptListener(new PrintStream(bytes, true, StandardCharsets.UTF_8));

        listener.onScriptRun(new ScriptRunListener.ScriptRun(
                "python", "a = 6 * 7\nprint(a)\n", "42\n", Duration.ofMillis(150)));
        listener.onScriptRun(new ScriptRunListener.ScriptRun(
                "bash", "true", "", Duration.ofMillis(3)));

        var text = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("script #1 (python)"), text);
        assertTrue(text.contains("│   1  a = 6 * 7"), text);
        assertTrue(text.contains("│   2  print(a)"), text);
        assertTrue(text.contains("output (150 ms)"), text);
        assertTrue(text.contains("│ 42"), text);
        assertTrue(text.contains("script #2 (bash)"), text);
        assertTrue(text.contains("(no output)"), text);
        assertFalse(text.contains("│   3"), "No trailing blank source line: " + text);
    }
}
