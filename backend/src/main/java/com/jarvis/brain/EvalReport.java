package com.jarvis.brain;

import java.time.Instant;
import java.util.List;

/** The result of running the golden eval suite: each case's verdict plus a pass tally. */
public record EvalReport(Instant ranAt, List<EvalResult> results, int passed, int total, String summary) {}
