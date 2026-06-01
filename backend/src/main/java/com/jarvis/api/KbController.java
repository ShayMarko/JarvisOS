package com.jarvis.api;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jarvis.kb.KbDocument;
import com.jarvis.kb.KnowledgeBaseService;
import com.jarvis.kb.SearchHit;

/** Knowledge Base / RAG endpoints (spec §10.2). */
@RestController
@RequestMapping("/api/kb")
public class KbController {

    private final KnowledgeBaseService kb;

    public KbController(KnowledgeBaseService kb) {
        this.kb = kb;
    }

    public record IndexRequest(String path, String title, String content) {}

    @GetMapping
    public List<KbDocument> documents() {
        return kb.documents();
    }

    @GetMapping("/search")
    public List<SearchHit> search(@RequestParam("q") String query,
                                  @RequestParam(name = "k", defaultValue = "5") int k) {
        return kb.search(query, k);
    }

    @PostMapping("/index")
    public Map<String, Object> index(@RequestBody IndexRequest request) {
        if (request.content() != null && !request.content().isBlank()) {
            KbDocument doc = kb.indexText(
                    request.title() != null ? "manual:" + request.title() : "manual:note",
                    request.title() != null ? request.title() : "Note", request.content());
            return Map.of("indexed", 1, "documentId", doc.getId(), "chunks", doc.getChunkCount());
        }
        int n = kb.indexPath(request.path());
        return Map.of("indexed", n, "path", request.path());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        kb.delete(id);
        return ResponseEntity.noContent().build();
    }
}
