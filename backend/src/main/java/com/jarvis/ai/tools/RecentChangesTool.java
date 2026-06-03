package com.jarvis.ai.tools;

import java.util.List;

import org.springframework.stereotype.Component;

import com.jarvis.ai.ToolSpec;
import com.jarvis.undo.UndoJournal;

import lombok.RequiredArgsConstructor;

/**
 * Lists the recent reversible changes (newest first) so Jarvis can answer "what did you just change?"
 * or "what can I undo?" before the user decides whether to undo.
 */
@Component
@RequiredArgsConstructor
public class RecentChangesTool implements Tool {

    private final UndoJournal undo;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("list_recent_changes",
                "List the recent changes Jarvis made that can be undone (newest first). "
                + "Use to answer 'what did you change?' or 'what can I undo?'.",
                "{\"type\":\"object\",\"properties\":{\"limit\":{\"type\":\"integer\"}}}");
    }

    @Override
    public String execute(String args) {
        List<String> recent = undo.recent(10);
        if (recent.isEmpty()) {
            return "No recent changes to undo.";
        }
        StringBuilder sb = new StringBuilder("Recent undoable changes (newest first):\n");
        int i = 1;
        for (String r : recent) {
            sb.append(i++).append(". ").append(r).append('\n');
        }
        return sb.toString().trim();
    }
}
