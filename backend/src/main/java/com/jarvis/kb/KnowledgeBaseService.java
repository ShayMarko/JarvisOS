package com.jarvis.kb;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.audit.AuditService;
import com.jarvis.config.JarvisLimitsProperties;
import com.jarvis.error.Exceptions.NotFoundException;
import com.jarvis.explorer.FileNode;
import com.jarvis.explorer.FileSystemService;

import lombok.RequiredArgsConstructor;

/**
 * The Knowledge Base / RAG service (spec §10.2): index documents into embedded
 * chunks, then retrieve by semantic similarity with source citations. Different
 * from Memory — this is knowledge from files, not facts about the user.
 */
@Service
@RequiredArgsConstructor
public class KnowledgeBaseService {

    private final KbDocumentRepository documents;
    private final KbChunkRepository chunks;
    private final EmbeddingModel embedder;
    private final FileSystemService fileSystem;
    private final ObjectMapper mapper;
    private final AuditService audit;
    private final JarvisLimitsProperties limits;

    public List<KbDocument> documents() {
        return documents.findAllByOrderByCreatedAtDesc();
    }

    /** Index a file or every file directly under a folder in the Jarvis Explorer. */
    @Transactional
    public int indexPath(String relativePath) {
        List<FileNode> targets = new ArrayList<>();
        FileNode self = tryStat(relativePath);
        if (self != null && self.directory()) {
            fileSystem.list(relativePath).stream()
                    .filter(n -> !n.directory())
                    .limit(limits.getKbMaxFilesPerFolder())
                    .forEach(targets::add);
        }
        if (targets.isEmpty()) {
            // treat as a single file
            indexFile(relativePath);
            return 1;
        }
        int count = 0;
        for (FileNode n : targets) {
            try {
                indexFile(n.path());
                count++;
            } catch (Exception ignored) {
                // skip non-text/binary files
            }
        }
        return count;
    }

    @Transactional
    public KbDocument indexFile(String relativePath) {
        String content = fileSystem.readText(relativePath).content();
        String title = relativePath.contains("/") ? relativePath.substring(relativePath.lastIndexOf('/') + 1) : relativePath;
        return indexText(relativePath, title, content);
    }

    @Transactional
    public KbDocument indexText(String source, String title, String content) {
        deleteBySource(source); // re-index cleanly

        List<String> parts = chunk(content);
        String docId = "doc_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        KbDocument doc = documents.save(new KbDocument(docId, source, title, parts.size()));

        List<KbChunk> toSave = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            float[] vec = embedder.embed(parts.get(i));
            toSave.add(new KbChunk("chk_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8),
                    docId, i, parts.get(i), writeVec(vec)));
        }
        chunks.saveAll(toSave);
        audit.record("KB", "kb:index", title, "OK", "source=" + source + "; chunks=" + parts.size());
        return doc;
    }

    public List<SearchHit> search(String query, int k) {
        float[] qv = embedder.embed(query);
        Map<String, KbDocument> docsById = documents.findAll().stream()
                .collect(Collectors.toMap(KbDocument::getId, d -> d));
        return chunks.findAll().stream()
                .map(c -> {
                    KbDocument d = docsById.get(c.getDocumentId());
                    return new SearchHit(c.getDocumentId(),
                            d != null ? d.getTitle() : "?", d != null ? d.getSource() : "?",
                            c.getOrdinal(), c.getContent(), EmbeddingModel.cosine(qv, readVec(c.getEmbedding())));
                })
                .filter(h -> h.score() > 0)
                .sorted(Comparator.comparingDouble(SearchHit::score).reversed())
                .limit(Math.max(1, k))
                .toList();
    }

    @Transactional
    public void delete(String documentId) {
        if (!documents.existsById(documentId)) {
            throw new NotFoundException("No KB document " + documentId);
        }
        chunks.deleteByDocumentId(documentId);
        documents.deleteById(documentId);
    }

    private void deleteBySource(String source) {
        documents.findBySource(source).forEach(d -> {
            chunks.deleteByDocumentId(d.getId());
            documents.deleteById(d.getId());
        });
    }

    private FileNode tryStat(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return new FileNode("", "", true, 0, java.time.Instant.EPOCH); // root = directory
        }
        String parent = relativePath.contains("/") ? relativePath.substring(0, relativePath.lastIndexOf('/')) : "";
        try {
            return fileSystem.list(parent).stream()
                    .filter(n -> n.path().equals(relativePath)).findFirst().orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> chunk(String content) {
        List<String> out = new ArrayList<>();
        String text = content == null ? "" : content.strip();
        if (text.isEmpty()) {
            out.add("");
            return out;
        }
        int pos = 0;
        while (pos < text.length()) {
            int end = Math.min(text.length(), pos + limits.getKbChunkSize());
            out.add(text.substring(pos, end));
            if (end == text.length()) {
                break;
            }
            pos = end - limits.getKbChunkOverlap();
        }
        return out;
    }

    private String writeVec(float[] vec) {
        try {
            return mapper.writeValueAsString(vec);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialise embedding", e);
        }
    }

    private float[] readVec(String json) {
        try {
            return mapper.readValue(json, float[].class);
        } catch (Exception e) {
            return new float[embedder.dimension()];
        }
    }
}
