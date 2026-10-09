package com.embabel.devoxx;

import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.EmbabelComponent;
import com.embabel.agent.api.common.ActionContext;
import com.embabel.agent.api.template.TemplatedPromptRunnerBuilder;
import com.embabel.agent.rag.tools.ToolishRag;
import com.embabel.agent.skills.Skills;
import com.embabel.chat.AssistantMessage;
import com.embabel.chat.Conversation;
import com.embabel.chat.UserMessage;

import java.util.List;
import java.util.Map;

/**
 * Chat action behind the shell's "chat" command.
 * Each user message triggers a response from Mycroft Holmes, whose system prompt is
 * the Jinja template at prompts/holmes/mycroft.jinja, with three references attached:
 * the RAG store (search tools), the skills (sandboxed script tools) and the script runner.
 */
@EmbabelComponent
public class HolmesChat {

    /** Template under classpath:/prompts, rendered into the system prompt. */
    static final String SYSTEM_PROMPT_TEMPLATE = "holmes/mycroft";

    /** The books in the corpus; the template lists them. */
    static final List<String> CANON = List.of(
            "A Study in Scarlet",
            "The Sign of the Four",
            "The Hound of the Baskervilles",
            "The Valley of Fear",
            "The Adventures of Sherlock Holmes",
            "The Memoirs of Sherlock Holmes",
            "The Return of Sherlock Holmes",
            "His Last Bow",
            "The Case-Book of Sherlock Holmes");

    static final int MAX_WORDS = 200;

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
        var reply = new TemplatedPromptRunnerBuilder(context, context.ai().withDefaultLlm())
                .withSystemPromptTemplate(SYSTEM_PROMPT_TEMPLATE, Map.of("canon", CANON, "maxWords", MAX_WORDS))
                .withReference(holmesRag.withListener(sources))
                .withReference(holmesSkills)
                .withReference(scriptRunner)
                .respond(conversation.getMessages());
        if (!sources.isEmpty()) {
            reply = new AssistantMessage(reply.getContent() + "\n\n" + sources.summary(reply.getContent()));
        }
        context.sendMessage(conversation.addMessage(reply));
    }
}
