package com.embabel.devoxx;

import com.embabel.agent.sandbox.SandboxSessionManager;
import com.embabel.agent.sandbox.ScratchTool;
import com.embabel.agent.sandbox.docker.DockerSandboxSessionManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class SandboxConfiguration {

    /**
     * Manages Docker containers for sandbox sessions. Nothing is started until
     * the first command runs, so the app boots without Docker.
     */
    @Bean
    SandboxSessionManager sandboxSessionManager() {
        return new DockerSandboxSessionManager();
    }

    /**
     * Embabel's scratch tool: a persistent container (default image
     * embabel/agent-sandbox:latest) that runs bash commands and keeps state
     * between calls. Spring closes it, and its container, on shutdown.
     */
    @Bean
    ScratchTool scratchTool(SandboxSessionManager sandboxSessionManager) {
        return new ScratchTool(sandboxSessionManager);
    }

    /**
     * Every script the LLM runs is pretty printed to the console, so the
     * audience sees the generated code and its output, not just the tool call.
     */
    @Bean
    HolmesScriptRunner holmesScriptRunner(ScratchTool scratchTool) {
        return new HolmesScriptRunner(scratchTool, RagConfiguration.CORPUS_DIR,
                new PrettyPrintingScriptListener(System.out));
    }
}
