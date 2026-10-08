package com.embabel.devoxx;

import com.embabel.agent.api.common.PromptRunner;
import com.embabel.agent.api.reference.LlmReference;
import com.embabel.agent.api.tool.Tool;

import java.util.List;

/**
 * Workaround for Embabel 1.5.3, where {@link PromptRunner#withReference} adds a
 * reference's tools twice: once as returned by {@code reference.tools()} and once
 * more renamed through the reference's tool object. The LLM then sees both
 * {@code holmes_textSearch} and {@code holmes_holmes_textSearch}.
 * <p>
 * This adds each tool exactly once, under the prefix the reference advertises in
 * its prompt contribution. Remove once the framework fix ships.
 */
final class ReferenceTools {

    private ReferenceTools() {
    }

    static PromptRunner withReferenceOnce(PromptRunner runner, LlmReference reference) {
        return runner
                .withTools(toolsOf(reference))
                .withPromptContributor(reference);
    }

    /**
     * The reference's tools, each named with the reference's tool prefix exactly once.
     */
    static List<Tool> toolsOf(LlmReference reference) {
        var prefix = reference.toolPrefix() + "_";
        return reference.tools().stream()
                .map(tool -> {
                    var name = tool.getDefinition().getName();
                    return name.startsWith(prefix) ? tool : tool.withName(prefix + name);
                })
                .toList();
    }
}
