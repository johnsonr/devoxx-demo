package com.embabel.devoxx;

import com.embabel.agent.skills.Skills;
import com.embabel.agent.skills.script.DockerSkillScriptExecutionEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class SkillsConfiguration {

    static final String SKILL_DIR = "skills/holmes-stats";

    /**
     * Skills following the Agent Skills specification (SKILL.md plus scripts).
     * Scripts run as LLM tools inside a Docker container: the script directory
     * is mounted read-only, input files land in INPUT_DIR, output in OUTPUT_DIR.
     */
    @Bean
    Skills holmesSkills() {
        return new Skills("holmes-skills", "Skills for analysing the Sherlock Holmes corpus")
                .withLocalSkill(SKILL_DIR)
                .withScriptExecutionEngine(new DockerSkillScriptExecutionEngine());
    }
}
