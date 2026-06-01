package com.jarvis.approval;

/**
 * The outcome of submitting or deciding an approval. {@code result} is the value
 * produced by the deferred action once it actually runs (null while pending or
 * if denied).
 */
public record ApprovalResult(ApprovalRequest request, Object result) {}
