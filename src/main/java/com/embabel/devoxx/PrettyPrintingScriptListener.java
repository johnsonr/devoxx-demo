package com.embabel.devoxx;

import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Prints every script the LLM ran as a numbered block: the source with line
 * numbers, then the sandbox output. Shows the audience exactly what code mode
 * executed, rather than the escaped JSON the tool call line displays.
 */
public final class PrettyPrintingScriptListener implements ScriptRunListener {

    private static final int WIDTH = 72;

    private final PrintStream out;
    private final AtomicInteger counter = new AtomicInteger();

    public PrettyPrintingScriptListener(PrintStream out) {
        this.out = out;
    }

    @Override
    public void onScriptRun(ScriptRun run) {
        out.print(format(run, counter.incrementAndGet()));
        out.flush();
    }

    static String format(ScriptRun run, int number) {
        var sb = new StringBuilder();
        sb.append(rule("┌─ script #" + number + " (" + run.language() + ") ")).append('\n');
        var lines = run.code().stripTrailing().split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            sb.append(String.format("│ %3d  %s%n", i + 1, lines[i]));
        }
        sb.append(rule("├─ output (" + run.duration().toMillis() + " ms) ")).append('\n');
        var output = run.output().stripTrailing();
        for (var line : (output.isEmpty() ? "(no output)" : output).split("\n", -1)) {
            sb.append("│ ").append(line).append('\n');
        }
        sb.append(rule("└")).append('\n');
        return sb.toString();
    }

    private static String rule(String prefix) {
        return prefix + "─".repeat(Math.max(0, WIDTH - prefix.length()));
    }
}
