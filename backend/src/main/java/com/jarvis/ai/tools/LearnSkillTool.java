package com.jarvis.ai.tools;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.ToolSpec;
import com.jarvis.skill.SkillService;

import lombok.RequiredArgsConstructor;

/**
 * Teaches Jarvis a reusable skill — a named procedure it can perform later with its existing tools.
 * The agent calls this when the user says "learn to do X" / "from now on, when I ask for X, do …",
 * so Jarvis extends its own abilities without any new code.
 */
@Component
@RequiredArgsConstructor
public class LearnSkillTool implements Tool {

    private final SkillService skills;
    private final ObjectMapper mapper;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("learn_skill",
                "Teach yourself a reusable skill (a procedure you can perform later with your tools). Use it "
                + "when the user teaches you how to do something they'll want again. Give a short kebab-case "
                + "'name', a one-line 'description', and clear step-by-step 'instructions' (which tools to use).",
                "{\"type\":\"object\",\"properties\":{"
                + "\"name\":{\"type\":\"string\",\"description\":\"short handle, e.g. weekly-report\"},"
                + "\"description\":{\"type\":\"string\",\"description\":\"one line: what it does\"},"
                + "\"instructions\":{\"type\":\"string\",\"description\":\"step-by-step how-to, naming the tools to use\"}},"
                + "\"required\":[\"name\",\"description\",\"instructions\"]}");
    }

    @Override
    public boolean mutates() {
        return true;
    }

    @Override
    public String execute(String args) {
        String name = ToolArgs.firstStr(mapper, args, "name", "skill", "title");
        String description = ToolArgs.firstStr(mapper, args, "description", "summary", "desc");
        String instructions = ToolArgs.firstStr(mapper, args, "instructions", "steps", "how", "procedure");
        if (name.isBlank() || instructions.isBlank()) {
            return "Error: a skill needs at least a \"name\" and \"instructions\".";
        }
        skills.learn(name, description, instructions);
        return "Learned the skill \"" + name + "\". I'll use it whenever you need it.";
    }
}
