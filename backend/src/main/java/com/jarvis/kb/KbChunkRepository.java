package com.jarvis.kb;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface KbChunkRepository extends JpaRepository<KbChunk, String> {

    List<KbChunk> findByDocumentId(String documentId);

    void deleteByDocumentId(String documentId);
}
