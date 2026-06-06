package com.jarvis.brain;

/** The graded outcome of one eval case. */
public record EvalResult(String id, String prompt, String expectation, String answer, boolean pass, String reason) {}
