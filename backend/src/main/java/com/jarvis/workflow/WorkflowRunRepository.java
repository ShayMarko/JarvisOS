package com.jarvis.workflow;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowRunRepository extends JpaRepository<WorkflowRun, String> {

    List<WorkflowRun> findAllByOrderByStartedAtDesc(Pageable pageable);

    List<WorkflowRun> findByWorkflowIdOrderByStartedAtDesc(String workflowId);
}
