package com.embabel.devoxx;

import java.time.Duration;

/**
 * Notified after every script the LLM runs in the sandbox.
 */
@FunctionalInterface
public interface ScriptRunListener {

    /**
     * One executed script: the language and source the LLM wrote, what the
     * sandbox returned, and how long the run took.
     */
    record ScriptRun(String language, String code, String output, Duration duration) {
    }

    ScriptRunListener NONE = run -> {
    };

    void onScriptRun(ScriptRun run);
}
