package com.jarvis.job;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobRepository extends JpaRepository<Job, String> {
    List<Job> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<Job> findByStatus(JobStatus status);

    long countByStatus(JobStatus status);
}
