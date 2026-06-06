package com.jarvis.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jarvis.brain.EvalCase;
import com.jarvis.brain.EvalReport;
import com.jarvis.brain.EvalService;

import lombok.RequiredArgsConstructor;

/**
 * Eval harness API. GET lists the golden suite; POST /run executes it through the real Brain and
 * returns graded results — run it before and after a model/provider swap to catch quality regressions.
 */
@RestController
@RequestMapping("/api/eval")
@RequiredArgsConstructor
public class EvalController {

    private final EvalService eval;

    @GetMapping
    public List<EvalCase> cases() {
        return eval.cases();
    }

    @PostMapping("/run")
    public EvalReport run() {
        return eval.run();
    }
}
