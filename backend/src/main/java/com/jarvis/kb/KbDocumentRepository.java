package com.jarvis.kb;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface KbDocumentRepository extends JpaRepository<KbDocument, String> {

    List<KbDocument> findAllByOrderByCreatedAtDesc();

    List<KbDocument> findBySource(String source);
}
