package com.embabel.devoxx;

import com.embabel.agent.api.annotation.LlmTool;
import com.embabel.agent.api.reference.LlmReference;
import com.embabel.agent.api.tool.Tool;
import com.embabel.agent.sandbox.ScratchTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Code mode: the LLM writes a script for a question the search tools cannot
 * answer, and runs it in the Embabel sandbox against the corpus.
 * <p>
 * Built on {@link ScratchTool}, which keeps one Docker container alive between
 * calls. On first use the corpus is copied into the container, so scripts read
 * the stories from {@code corpus/*.md} in the working directory. Scripts arrive
 * on stdin and are written to a file before running, so nothing is shell-quoted.
 */
public class HolmesScriptRunner implements LlmReference {

    static final String CORPUS_IN_SANDBOX = "corpus";

    private static final Logger logger = LoggerFactory.getLogger(HolmesScriptRunner.class);

    private static final Map<String, Language> LANGUAGES = Map.of(
            "python", new Language("script.py", "python3 script.py"),
            "bash", new Language("script.sh", "bash script.sh"),
            "node", new Language("script.js", "node script.js"),
            "javascript", new Language("script.js", "node script.js"));

    private record Language(String file, String run) {
    }

    private final ScratchTool scratchTool;
    private final Path corpusDir;
    private final ScriptRunListener listener;
    private final JsonMapper json = JsonMapper.builder().build();
    private boolean corpusStaged;

    public HolmesScriptRunner(ScratchTool scratchTool, Path corpusDir) {
        this(scratchTool, corpusDir, ScriptRunListener.NONE);
    }

    /**
     * @param listener notified after every script run, for example to print the
     *                 script and its output
     */
    public HolmesScriptRunner(ScratchTool scratchTool, Path corpusDir, ScriptRunListener listener) {
        this.scratchTool = scratchTool;
        this.corpusDir = corpusDir;
        this.listener = listener;
    }

    @Override
    public String getName() {
        return "sandbox";
    }

    @Override
    public String getDescription() {
        return "A Docker sandbox holding the corpus, where scripts you write can run";
    }

    @Override
    public String notes() {
        return """
                Use run_script for anything the search tools cannot answer directly:
                statistics, comparisons across stories, word or dialogue counts, rankings.
                The stories are Markdown files in corpus/ (one per book), each story or chapter
                starting with a "## " heading. Python 3 with the standard library is available;
                there is no pandas. Print results, then report them exactly.""";
    }

    @Override
    public List<Tool> tools() {
        return Tool.fromInstance(this);
    }

    @LlmTool(name = "run_script", description = """
            Write a script and run it in the sandbox against the corpus.
            Returns the script's output, or its error output and exit code if it failed.""")
    public String runScript(
            @LlmTool.Param(description = "python, bash or node") String language,
            @LlmTool.Param(description = "Full source of the script to run") String code) {
        var lang = LANGUAGES.get(language.toLowerCase(Locale.ROOT).trim());
        if (lang == null) {
            return "Unsupported language '" + language + "'. Use python, bash or node.";
        }
        try {
            stageCorpus();
            var written = run("cat > " + lang.file(), code);
            if (written instanceof Tool.Result.Error error) {
                return "Could not write script: " + error.getMessage();
            }
            var started = System.nanoTime();
            var output = render(run(lang.run(), null));
            var duration = Duration.ofNanos(System.nanoTime() - started);
            logger.info("Ran {} script in {} ms:\n{}\n--- output ---\n{}", language, duration.toMillis(), code, output);
            listener.onScriptRun(new ScriptRunListener.ScriptRun(language, code, output, duration));
            return output;
        } catch (Exception e) {
            logger.warn("Script run failed", e);
            return "Sandbox error: " + e.getMessage();
        }
    }

    /**
     * Copy every corpus file into the container once. Later calls reuse the files,
     * like everything else in the sandbox session.
     */
    synchronized void stageCorpus() throws IOException {
        if (corpusStaged) {
            return;
        }
        try (Stream<Path> files = Files.list(corpusDir)) {
            for (var file : files.filter(Files::isRegularFile).sorted().toList()) {
                var target = CORPUS_IN_SANDBOX + "/" + file.getFileName();
                var result = run("mkdir -p " + CORPUS_IN_SANDBOX + " && cat > " + target, Files.readString(file));
                if (result instanceof Tool.Result.Error error) {
                    throw new IllegalStateException("Could not copy " + file + " into the sandbox: " + error.getMessage());
                }
                logger.info("Staged {} in sandbox as {}", file.getFileName(), target);
            }
        }
        corpusStaged = true;
    }

    private Tool.Result run(String command, String stdin) {
        var input = stdin == null
                ? Map.of("command", command)
                : Map.of("command", command, "stdin", stdin);
        return scratchTool.call(json.writeValueAsString(input));
    }

    private static String render(Tool.Result result) {
        if (result instanceof Tool.Result.Text text) {
            return text.getContent();
        }
        if (result instanceof Tool.Result.Error error) {
            return "Error: " + error.getMessage();
        }
        return result.toString();
    }
}
