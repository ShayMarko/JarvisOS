package com.jarvis.ai.tools;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.ToolSpec;
import com.jarvis.explorer.FileSystemService;
import com.jarvis.local.MacActions;

/**
 * macOS Local Action tools (spec §8) — the agent decides when to launch apps,
 * reveal files, screenshot, use the clipboard or Spotlight. Each fails soft so
 * a non-Mac host or a bad call never breaks the agent loop.
 */
public final class MacTools {

    private MacTools() {}

    private static String arg(ObjectMapper m, String json, String key) {
        try {
            return m.readTree(json == null || json.isBlank() ? "{}" : json).path(key).asText("");
        } catch (Exception e) {
            return "";
        }
    }

    @Component
    public static class OpenAppTool implements Tool {
        private final MacActions mac;
        private final ObjectMapper mapper;
        public OpenAppTool(MacActions mac, ObjectMapper mapper) { this.mac = mac; this.mapper = mapper; }

        @Override
        public ToolSpec spec() {
            return new ToolSpec("open_app", "Open a macOS application by name.",
                    "{\"type\":\"object\",\"properties\":{\"app\":{\"type\":\"string\"}},\"required\":[\"app\"]}");
        }

        @Override
        public String execute(String args) {
            try { return mac.openApp(arg(mapper, args, "app")); }
            catch (Exception e) { return "Error: " + e.getMessage(); }
        }
    }

    @Component
    public static class RevealTool implements Tool {
        private final MacActions mac;
        private final FileSystemService fs;
        private final ObjectMapper mapper;
        public RevealTool(MacActions mac, FileSystemService fs, ObjectMapper mapper) {
            this.mac = mac; this.fs = fs; this.mapper = mapper;
        }

        @Override
        public ToolSpec spec() {
            return new ToolSpec("reveal_in_finder", "Reveal a Jarvis Explorer file in Finder.",
                    "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"}},\"required\":[\"path\"]}");
        }

        @Override
        public String execute(String args) {
            try { return mac.reveal(fs.resolveExisting(arg(mapper, args, "path"))); }
            catch (Exception e) { return "Error: " + e.getMessage(); }
        }
    }

    @Component
    public static class ScreenshotTool implements Tool {
        private final MacActions mac;
        private final ObjectMapper mapper;
        public ScreenshotTool(MacActions mac, ObjectMapper mapper) { this.mac = mac; this.mapper = mapper; }

        @Override
        public ToolSpec spec() {
            return new ToolSpec("screenshot", "Capture a screenshot to the Screenshots folder.",
                    "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\",\"description\":\"optional file name\"}}}");
        }

        @Override
        public String execute(String args) {
            try { return mac.screenshot(arg(mapper, args, "name")); }
            catch (Exception e) { return "Error: " + e.getMessage(); }
        }
    }

    @Component
    public static class ClipboardReadTool implements Tool {
        private final MacActions mac;
        public ClipboardReadTool(MacActions mac) { this.mac = mac; }

        @Override
        public ToolSpec spec() {
            return new ToolSpec("clipboard_read", "Read the macOS clipboard contents.",
                    "{\"type\":\"object\",\"properties\":{}}");
        }

        @Override
        public String execute(String args) {
            try { return mac.clipboardRead(); }
            catch (Exception e) { return "Error: " + e.getMessage(); }
        }
    }

    @Component
    public static class ClipboardWriteTool implements Tool {
        private final MacActions mac;
        private final ObjectMapper mapper;
        public ClipboardWriteTool(MacActions mac, ObjectMapper mapper) { this.mac = mac; this.mapper = mapper; }

        @Override
        public ToolSpec spec() {
            return new ToolSpec("clipboard_write", "Write text to the macOS clipboard.",
                    "{\"type\":\"object\",\"properties\":{\"text\":{\"type\":\"string\"}},\"required\":[\"text\"]}");
        }

        @Override
        public String execute(String args) {
            try { return mac.clipboardWrite(arg(mapper, args, "text")); }
            catch (Exception e) { return "Error: " + e.getMessage(); }
        }
    }

    @Component
    public static class SayTool implements Tool {
        private final MacActions mac;
        private final ObjectMapper mapper;
        public SayTool(MacActions mac, ObjectMapper mapper) { this.mac = mac; this.mapper = mapper; }

        @Override
        public ToolSpec spec() {
            return new ToolSpec("say", "Speak text aloud on the Mac (text-to-speech).",
                    "{\"type\":\"object\",\"properties\":{\"text\":{\"type\":\"string\"}},\"required\":[\"text\"]}");
        }

        @Override
        public String execute(String args) {
            try { return mac.say(arg(mapper, args, "text")); }
            catch (Exception e) { return "Error: " + e.getMessage(); }
        }
    }

    @Component
    public static class ImageConvertTool implements Tool {
        private final MacActions mac;
        private final ObjectMapper mapper;
        public ImageConvertTool(MacActions mac, ObjectMapper mapper) { this.mac = mac; this.mapper = mapper; }

        @Override
        public ToolSpec spec() {
            return new ToolSpec("image_convert", "Convert an Explorer image to another format (jpeg/png/…) via sips.",
                    "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"},\"format\":{\"type\":\"string\"}},\"required\":[\"path\",\"format\"]}");
        }

        @Override
        public String execute(String args) {
            try { return mac.convertImage(arg(mapper, args, "path"), arg(mapper, args, "format")); }
            catch (Exception e) { return "Error: " + e.getMessage(); }
        }
    }

    @Component
    public static class SpotlightTool implements Tool {
        private final MacActions mac;
        private final ObjectMapper mapper;
        public SpotlightTool(MacActions mac, ObjectMapper mapper) { this.mac = mac; this.mapper = mapper; }

        @Override
        public ToolSpec spec() {
            return new ToolSpec("spotlight_search", "Search the Mac with Spotlight (mdfind) by name.",
                    "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}},\"required\":[\"query\"]}");
        }

        @Override
        public String execute(String args) {
            try { return mac.spotlight(arg(mapper, args, "query")); }
            catch (Exception e) { return "Error: " + e.getMessage(); }
        }
    }
}
