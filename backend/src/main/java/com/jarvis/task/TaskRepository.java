package com.jarvis.task;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, String> {

    List<Task> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByStatus(TaskStatus status);
}
