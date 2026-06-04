package com.jarvis.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jarvis.timeline.TimelineEntry;
import com.jarvis.timeline.TimelineService;

import lombok.RequiredArgsConstructor;

/** Episodic timeline — per-day roll-ups of what Jarvis did (surfaces the same data as timeline_recall). */
@RestController
@RequestMapping("/api/timeline")
@RequiredArgsConstructor
public class TimelineController {

    private final TimelineService timeline;

    @GetMapping
    public List<TimelineEntry> recent(@RequestParam(name = "days", defaultValue = "7") int days) {
        return timeline.recent(days);
    }
}
