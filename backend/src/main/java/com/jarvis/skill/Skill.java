package com.jarvis.skill;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

/**
 * A skill Jarvis has been taught — a named, reusable procedure ("how to do X") it can recall and
 * perform later using its existing tools. This is how Jarvis extends itself without us shipping code:
 * the user (or Jarvis) teaches a procedure once; it's remembered and executed on demand thereafter.
 */
@Entity
@Table(name = "skill")
@Getter
@Setter
public class Skill {

    @Id
    private String id;

    /** Short, unique handle ("convert-invoices", "weekly-report"). */
    @Column(nullable = false)
    private String name;

    /** One line: what this skill does (shown in the roster so the model knows it exists). */
    @Column(nullable = false)
    private String description;

    /** The step-by-step how-to the model follows (which tools to use, in what order). */
    @Column(nullable = false, length = 8000)
    private String instructions;

    /** How many times it's been recalled — popularity signal. */
    private int uses;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
