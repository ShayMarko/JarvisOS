package com.jarvis.task;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, String> {

    List<Task> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<Task> findByStatus(TaskStatus status);

    long countByStatus(TaskStatus status);
}
