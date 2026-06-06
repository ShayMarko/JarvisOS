package com.jarvis.brain;

/** A single golden eval: a prompt to send the Brain and a plain-English expectation the judge grades against. */
public record EvalCase(String id, String prompt, String expectation) {}
