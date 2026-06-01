package com.jarvis.command.handlers;

import org.springframework.stereotype.Component;

import com.jarvis.command.CommandCategory;
import com.jarvis.command.CommandContext;
import com.jarvis.command.CommandDefinition;
import com.jarvis.command.CommandHandler;
import com.jarvis.command.CommandResult;
import com.jarvis.local.MacActions;

import java.util.List;

/** macOS Local Action slash commands (spec §5.2): /openapp, /clipboard, /screenshot. */
public final class LocalActionHandlers {

    private LocalActionHandlers() {}

    @Component
    public static class OpenAppHandler implements CommandHandler {
        private final MacActions mac;
        public OpenAppHandler(MacActions mac) { this.mac = mac; }

        @Override
        public CommandDefinition definition() {
            return new CommandDefinition("openapp", "/openapp", List.of("open app", "launch"),
                    "Open a local application by name.", List.of("name"),
                    List.of("apps:launch"), true, CommandCategory.SYSTEM);
        }

        @Override
        public CommandResult handle(CommandContext context) {
            String app = context.argLine().trim();
            if (app.isEmpty()) {
                return CommandResult.error("Usage: /openapp <application name>");
            }
            return CommandResult.message(mac.openApp(app));
        }
    }

    @Component
    public static class ClipboardHandler implements CommandHandler {
        private final MacActions mac;
        public ClipboardHandler(MacActions mac) { this.mac = mac; }

        @Override
        public CommandDefinition definition() {
            return new CommandDefinition("clipboard", "/clipboard", List.of("read clipboard"),
                    "Read or set the clipboard. With text, copies it; otherwise reads it.",
                    List.of("text"), List.of("clipboard"), true, CommandCategory.SYSTEM);
        }

        @Override
        public CommandResult handle(CommandContext context) {
            String text = context.argLine().trim();
            if (text.isEmpty()) {
                return CommandResult.message("📋 " + mac.clipboardRead());
            }
            return CommandResult.message(mac.clipboardWrite(text));
        }
    }

    @Component
    public static class SayHandler implements CommandHandler {
        private final MacActions mac;
        public SayHandler(MacActions mac) { this.mac = mac; }

        @Override
        public CommandDefinition definition() {
            return new CommandDefinition("say", "/say", List.of("speak"),
                    "Speak text aloud (macOS text-to-speech).", List.of("text"),
                    List.of("audio:tts"), true, CommandCategory.SYSTEM);
        }

        @Override
        public CommandResult handle(CommandContext context) {
            String text = context.argLine().trim();
            if (text.isEmpty()) {
                return CommandResult.error("Usage: /say <text>");
            }
            return CommandResult.message(mac.say(text));
        }
    }

    @Component
    public static class ScreenshotHandler implements CommandHandler {
        private final MacActions mac;
        public ScreenshotHandler(MacActions mac) { this.mac = mac; }

        @Override
        public CommandDefinition definition() {
            return new CommandDefinition("screenshot", "/screenshot", List.of("take a screenshot"),
                    "Capture a screenshot to the Screenshots folder.", List.of("name"),
                    List.of("screen:capture"), true, CommandCategory.SYSTEM);
        }

        @Override
        public CommandResult handle(CommandContext context) {
            return CommandResult.message(mac.screenshot(context.argLine().trim()));
        }
    }
}
