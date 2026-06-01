package com.jarvis.command;

import java.util.List;

/**
 * The parsed input handed to a {@link CommandHandler}.
 *
 * @param raw   the full original input, e.g. "/openapp Safari"
 * @param slash the matched command token, e.g. "/openapp"
 * @param args  the remaining whitespace-separated arguments
 */
public record CommandContext(String raw, String slash, List<String> args) {

    public String argLine() {
        return String.join(" ", args);
    }
}
