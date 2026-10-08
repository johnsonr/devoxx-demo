package com.embabel.devoxx;

import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.EmbabelComponent;
import com.embabel.agent.api.common.ActionContext;
import com.embabel.agent.rag.tools.ToolishRag;
import com.embabel.agent.skills.Skills;
import com.embabel.chat.AssistantMessage;
import com.embabel.chat.Conversation;
import com.embabel.chat.UserMessage;

/**
 * Chat action behind the shell's "chat" command.
 * Each user message triggers a response with two references attached:
 * the RAG store (search tools) and the skills (sandboxed script tools).
 */
@EmbabelComponent
public class HolmesChat {

    static final String SYSTEM_PROMPT = """
            You are a knowledgeable guide to the Sherlock Holmes short stories: The Adventures, The Memoirs,
            The Return, His Last Bow and The Case-Book.
            Answer only from the story text: search it with the tools before answering,
            and quote briefly where useful. If the stories do not cover something, say so.
            For counting mentions of names, use the holmes-stats skill. For any other
            computation over the text, write a script and run it with run_script.
            Never estimate numbers.
            Keep answers under 200 words.
            """;

    private final ToolishRag holmesRag;
    private final Skills holmesSkills;
    private final HolmesScriptRunner scriptRunner;

    public HolmesChat(ToolishRag holmesRag, Skills holmesSkills, HolmesScriptRunner scriptRunner) {
        this.holmesRag = holmesRag;
        this.holmesSkills = holmesSkills;
        this.scriptRunner = scriptRunner;
    }

    @Action(canRerun = true, trigger = UserMessage.class)
    void respond(Conversation conversation, ActionContext context) {
        // A fresh collector per turn: every search the LLM runs reports its results here
        var sources = new RetrievedSources();
        var runner = context.ai().withDefaultLlm();
        runner = ReferenceTools.withReferenceOnce(runner, holmesRag.withListener(sources));
        runner = ReferenceTools.withReferenceOnce(runner, holmesSkills);
        runner = ReferenceTools.withReferenceOnce(runner, scriptRunner);
        var reply = runner
                .withSystemPrompt(SYSTEM_PROMPT)
                .respond(conversation.getMessages());
        if (!sources.isEmpty()) {
            reply = new AssistantMessage(reply.getContent() + "\n\n" + sources.summary());
        }
        context.sendMessage(conversation.addMessage(reply));
    }
}
