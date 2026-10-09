package com.embabel.devoxx;

import com.embabel.common.textio.template.JinjavaTemplateRenderer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Renders the system prompt template the way the platform does: from
 * classpath:/prompts with the .jinja suffix added.
 */
class MycroftPromptTest {

    @Test
    void mycroftIntroducesHimselfAndKnowsTheCanon() {
        var prompt = new JinjavaTemplateRenderer().renderLoadedTemplate(
                HolmesChat.SYSTEM_PROMPT_TEMPLATE,
                Map.of("canon", HolmesChat.CANON, "maxWords", HolmesChat.MAX_WORDS));

        assertTrue(prompt.contains("You are Mycroft Holmes"), prompt);
        assertTrue(prompt.contains("I have observed my brother's career with great interest, and always pride myself\n"
                + "in scrupulous attention to detail."), prompt);
        for (var book : HolmesChat.CANON) {
            assertTrue(prompt.contains("- " + book), prompt);
        }
        assertTrue(prompt.contains("under 200 words"), prompt);
        assertFalse(prompt.contains("{{"), "Unrendered template syntax: " + prompt);
    }
}
