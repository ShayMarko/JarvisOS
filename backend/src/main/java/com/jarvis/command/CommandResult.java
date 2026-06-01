package com.jarvis.command;

/**
 * The outcome of executing a command. {@code type} tells the client how to
 * render {@code data} (e.g. "help", "files", "status", "settings", "message").
 */
public record CommandResult(Status status, String type, String message, Object data) {

    public enum Status { OK, ERROR, PENDING_APPROVAL }

    public static CommandResult ok(String type, String message, Object data) {
        return new CommandResult(Status.OK, type, message, data);
    }

    public static CommandResult message(String message) {
        return new CommandResult(Status.OK, "message", message, null);
    }

    public static CommandResult error(String message) {
        return new CommandResult(Status.ERROR, "error", message, null);
    }
}
