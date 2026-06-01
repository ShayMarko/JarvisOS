package com.jarvis.kb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.audit.AuditService;
import com.jarvis.explorer.FileSystemService;

class KnowledgeBaseServiceTest {

    private final Map<String, KbDocument> docStore = new LinkedHashMap<>();
    private final Map<String, KbChunk> chunkStore = new LinkedHashMap<>();
    private KnowledgeBaseService kb;

    @BeforeEach
    void setUp() {
        KbDocumentRepository docs = mock(KbDocumentRepository.class);
        KbChunkRepository chunks = mock(KbChunkRepository.class);

        when(docs.save(any(KbDocument.class))).thenAnswer(i -> {
            KbDocument d = i.getArgument(0);
            docStore.put(d.getId(), d);
            return d;
        });
        when(docs.findAll()).thenAnswer(i -> new ArrayList<>(docStore.values()));
        when(docs.findBySource(anyString())).thenAnswer(i ->
                docStore.values().stream().filter(d -> d.getSource().equals(i.getArgument(0))).toList());
        when(chunks.saveAll(any())).thenAnswer(i -> {
            for (KbChunk c : (Iterable<KbChunk>) i.getArgument(0)) chunkStore.put(c.getId(), c);
            return null;
        });
        when(chunks.findAll()).thenAnswer(i -> new ArrayList<>(chunkStore.values()));

        kb = new KnowledgeBaseService(docs, chunks, new HashingEmbeddingModel(),
                mock(FileSystemService.class), new ObjectMapper(), mock(AuditService.class),
                new com.jarvis.config.JarvisLimitsProperties());
    }

    @Test
    void indexesAndRetrievesTheMostRelevantDocument() {
        kb.indexText("manual:pricing", "Pricing plan",
                "Our Q4 enterprise pricing plan raises the price of the top tier to ninety nine dollars per seat.");
        kb.indexText("manual:recipe", "Sourdough recipe",
                "To bake sourdough bread, feed your starter, mix flour and water, and proof overnight.");

        List<SearchHit> hits = kb.search("how much does the enterprise plan cost", 3);

        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).title()).isEqualTo("Pricing plan");
        assertThat(hits.get(0).score()).isGreaterThan(0.0);
    }
}
