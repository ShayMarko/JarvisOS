package com.jarvis.workflow;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowRepository extends JpaRepository<Workflow, String> {

    List<Workflow> findAllByOrderByCreatedAtDesc();

    List<Workflow> findByTriggerTypeAndEnabledTrue(TriggerType triggerType);
}
