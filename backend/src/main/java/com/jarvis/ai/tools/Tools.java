package com.jarvis.ai.tools;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.ToolSpec;
import com.jarvis.explorer.FileNode;
import com.jarvis.explorer.FileSystemService;
import com.jarvis.kb.KnowledgeBaseService;
import com.jarvis.kb.SearchHit;
import com.jarvis.memory.Memory;
import com.jarvis.memory.MemoryDraft;
import com.jarvis.memory.MemoryService;
import com.jarvis.system.SystemMonitorService;

/**
 * The Phase-6 tool set — each wraps an existing Capability so agents can act,
 * not just talk. Tools fail soft (return an error string) so a bad call never
 * crashes the agent loop.
 */
public final class Tools {

    private Tools() {}

    private static String arg(ObjectMapper mapper, String json, String key) {
        try {
            return mapper.readTree(json == null || json.isBlank() ? "{}" : json).path(key).asText("");
        } catch (Exception e) {
            return "";
        }
    }

    @Component
    public static class ListFilesTool implements Tool {
        private final FileSystemService fs;
        private final ObjectMapper mapper;

        public ListFilesTool(FileSystemService fs, ObjectMapper mapper) {
            this.fs = fs;
            this.mapper = mapper;
        }

        @Override
        public ToolSpec spec() {
            return new ToolSpec("list_files", "List files/folders in the Jarvis Explorer.",
                    "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\",\"description\":\"relative path, optional\"}}}");
        }

        @Override
        public String execute(String args) {
            try {
                String path = arg(mapper, args, "path");
                List<FileNode> nodes = fs.list(path);
                if (nodes.isEmpty()) {
                    return "(/" + path + " is empty)";
                }
                return "/" + path + ":\n" + nodes.stream()
                        .map(n -> (n.directory() ? "📁 " : "📄 ") + n.name())
                        .reduce((a, b) -> a + "\n" + b).orElse("");
            } catch (Exception e) {
                return "Error listing files: " + e.getMessage();
            }
        }
    }

    @Component
    public static class ReadFileTool implements Tool {
        private final FileSystemService fs;
        private final ObjectMapper mapper;

        public ReadFileTool(FileSystemService fs, ObjectMapper mapper) {
            this.fs = fs;
            this.mapper = mapper;
        }

        @Override
        public ToolSpec spec() {
            return new ToolSpec("read_file", "Read the text contents of a file.",
                    "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"}},\"required\":[\"path\"]}");
        }

        @Override
        public String execute(String args) {
            try {
                String content = fs.readText(arg(mapper, args, "path")).content();
                return content.length() > 1500 ? content.substring(0, 1500) + "\n…(truncated)" : content;
            } catch (Exception e) {
                return "Error reading file: " + e.getMessage();
            }
        }
    }

    @Component
    public static class WriteFileTool implements Tool {
        private final FileSystemService fs;
        private final ObjectMapper mapper;

        public WriteFileTool(FileSystemService fs, ObjectMapper mapper) {
            this.fs = fs;
            this.mapper = mapper;
        }

        @Override
        public ToolSpec spec() {
            return new ToolSpec("write_file", "Create or overwrite a text file in the Jarvis Explorer.",
                    "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"},"
                    + "\"content\":{\"type\":\"string\"}},\"required\":[\"path\",\"content\"]}");
        }

        @Override
        public String execute(String args) {
            try {
                String path = arg(mapper, args, "path");
                fs.writeText(path, arg(mapper, args, "content"));
                return "Wrote " + path;
            } catch (Exception e) {
                return "Error writing file: " + e.getMessage();
            }
        }
    }

    @Component
    public static class SearchFilesTool implements Tool {
        private final FileSystemService fs;
        private final ObjectMapper mapper;

        public SearchFilesTool(FileSystemService fs, ObjectMapper mapper) {
            this.fs = fs;
            this.mapper = mapper;
        }

        @Override
        public ToolSpec spec() {
            return new ToolSpec("search_files", "Search files by name across the Jarvis Explorer.",
                    "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}},\"required\":[\"query\"]}");
        }

        @Override
        public String execute(String args) {
            try {
                List<FileNode> hits = fs.search(arg(mapper, args, "query"), "");
                if (hits.isEmpty()) {
                    return "No matching files.";
                }
                return hits.stream().map(FileNode::path).reduce((a, b) -> a + "\n" + b).orElse("");
            } catch (Exception e) {
                return "Error searching: " + e.getMessage();
            }
        }
    }

    @Component
    public static class SystemStatusTool implements Tool {
        private final SystemMonitorService monitor;

        public SystemStatusTool(SystemMonitorService monitor) {
            this.monitor = monitor;
        }

        @Override
        public ToolSpec spec() {
            return new ToolSpec("system_status", "Get current CPU/memory/disk status.",
                    "{\"type\":\"object\",\"properties\":{}}");
        }

        @Override
        @SuppressWarnings("unchecked")
        public String execute(String args) {
            Map<String, Object> s = monitor.snapshot();
            Map<String, Object> cpu = (Map<String, Object>) s.get("cpu");
            Map<String, Object> mem = (Map<String, Object>) s.get("memory");
            long usedGb = (long) mem.get("usedPhysicalBytes") / (1024 * 1024 * 1024);
            long totalGb = (long) mem.get("totalPhysicalBytes") / (1024 * 1024 * 1024);
            return "OS: " + s.get("os")
                    + "; CPU cores: " + cpu.get("availableProcessors")
                    + "; system CPU load: " + cpu.get("systemCpuLoad")
                    + "; RAM: " + usedGb + "/" + totalGb + " GB"
                    + "; health: " + s.get("jarvisHealth");
        }
    }

    @Component
    public static class MemorySearchTool implements Tool {
        private final MemoryService memory;
        private final ObjectMapper mapper;

        public MemorySearchTool(MemoryService memory, ObjectMapper mapper) {
            this.memory = memory;
            this.mapper = mapper;
        }

        @Override
        public ToolSpec spec() {
            return new ToolSpec("memory_search", "Search the user's stored memories.",
                    "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\",\"description\":\"optional\"}}}");
        }

        @Override
        public String execute(String args) {
            List<Memory> hits = memory.list(arg(mapper, args, "query"));
            if (hits.isEmpty()) {
                return "No memories found.";
            }
            return hits.stream().limit(5)
                    .map(m -> "- " + m.getTitle() + ": " + m.getContent())
                    .reduce((a, b) -> a + "\n" + b).orElse("");
        }
    }

    @Component
    public static class MemoryWriteTool implements Tool {
        private final MemoryService memory;
        private final ObjectMapper mapper;

        public MemoryWriteTool(MemoryService memory, ObjectMapper mapper) {
            this.memory = memory;
            this.mapper = mapper;
        }

        @Override
        public ToolSpec spec() {
            return new ToolSpec("memory_write",
                    "Save a durable fact about the user so you remember it in future conversations.",
                    "{\"type\":\"object\",\"properties\":{\"title\":{\"type\":\"string\"},"
                    + "\"content\":{\"type\":\"string\"},\"category\":{\"type\":\"string\",\"description\":\"e.g. preference, fact, project\"}},"
                    + "\"required\":[\"title\",\"content\"]}");
        }

        @Override
        public String execute(String args) {
            try {
                String title = arg(mapper, args, "title");
                String content = arg(mapper, args, "content");
                String category = arg(mapper, args, "category");
                if (content.isBlank()) {
                    return "Nothing to remember (empty content).";
                }
                memory.create(new MemoryDraft(category.isBlank() ? "fact" : category,
                        title.isBlank() ? "Note" : title, content, "chat", null, null, null, null, null));
                return "Remembered: " + (title.isBlank() ? content : title);
            } catch (Exception e) {
                return "Error saving memory: " + e.getMessage();
            }
        }
    }

    @Component
    public static class KbSearchTool implements Tool {
        private final KnowledgeBaseService kb;
        private final ObjectMapper mapper;

        public KbSearchTool(KnowledgeBaseService kb, ObjectMapper mapper) {
            this.kb = kb;
            this.mapper = mapper;
        }

        @Override
        public ToolSpec spec() {
            return new ToolSpec("kb_search",
                    "Search the indexed Knowledge Base (the user's documents) and return relevant passages with sources.",
                    "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}},\"required\":[\"query\"]}");
        }

        @Override
        public String execute(String args) {
            try {
                List<SearchHit> hits = kb.search(arg(mapper, args, "query"), 4);
                if (hits.isEmpty()) {
                    return "No relevant documents in the Knowledge Base.";
                }
                StringBuilder sb = new StringBuilder();
                for (SearchHit h : hits) {
                    String snippet = h.content().length() > 400 ? h.content().substring(0, 400) + "…" : h.content();
                    sb.append("[").append(h.title()).append("] (score ")
                            .append(String.format("%.2f", h.score())).append(")\n")
                            .append(snippet).append("\n\n");
                }
                return sb.toString().trim();
            } catch (Exception e) {
                return "Error searching knowledge base: " + e.getMessage();
            }
        }
    }
}
