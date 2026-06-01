package com.jarvis.memory;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MemoryRepository extends JpaRepository<Memory, String> {

    List<Memory> findAllByOrderByUpdatedAtDesc();
}
