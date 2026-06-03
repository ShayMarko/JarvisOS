package com.jarvis.ai.tools;

import org.springframework.stereotype.Component;

import com.jarvis.ai.ToolSpec;
import com.jarvis.undo.UndoJournal;

import lombok.RequiredArgsConstructor;

/**
 * Conversational undo — "undo that", "revert the last change". Reverses the most recent reversible
 * action (a file write/create/delete) using the recipe stored in the {@link UndoJournal}.
 */
@Component
@RequiredArgsConstructor
public class UndoLastTool implements Tool {

    private final UndoJournal undo;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("undo_last",
                "Undo the most recent change Jarvis made (e.g. the last file it created, edited or deleted). "
                + "Use when the user says 'undo that', 'revert the last change', or similar.",
                "{\"type\":\"object\",\"properties\":{}}");
    }

    @Override
    public boolean mutates() {
        return true;
    }

    @Override
    public String execute(String args) {
        return undo.undoLast();
    }
}
