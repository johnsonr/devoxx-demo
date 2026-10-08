package com.embabel.devoxx;

import com.embabel.agent.api.invocation.AgentInvocation;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.rag.lucene.LuceneSearchOperations;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

@ShellComponent
record DevoxxShell(AgentPlatform agentPlatform, LuceneSearchOperations luceneSearchOperations) {

    @ShellMethod("Investigate a question about the stories: GOAP plans evidence -> deduction -> case file")
    String investigate(
            @ShellOption(defaultValue = "What was the real purpose of the Red-Headed League?") String question) {
        var caseFile = AgentInvocation
                .create(agentPlatform, CaseAgent.CaseFile.class)
                .invoke(new UserInput(question));
        return caseFile.getContent();
    }

    @ShellMethod("Show Lucene store statistics")
    String corpus() {
        return luceneSearchOperations.info().toString();
    }
}
