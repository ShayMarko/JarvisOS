package com.jarvis.api;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jarvis.undo.UndoJournal;

import lombok.RequiredArgsConstructor;

/** Undo journal — list the recent reversible actions and reverse the most recent one ("undo that"). */
@RestController
@RequestMapping("/api/undo")
@RequiredArgsConstructor
public class UndoController {

    private final UndoJournal journal;

    @GetMapping
    public Map<String, Object> list() {
        List<String> recent = journal.recent(20);
        return Map.of("count", journal.size(), "recent", recent);
    }

    @PostMapping
    public Map<String, Object> undoLast() {
        String result = journal.undoLast();
        return Map.of("result", result, "count", journal.size());
    }
}
