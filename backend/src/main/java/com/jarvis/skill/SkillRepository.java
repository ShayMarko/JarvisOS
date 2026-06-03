package com.jarvis.skill;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SkillRepository extends JpaRepository<Skill, String> {

    Optional<Skill> findByNameIgnoreCase(String name);

    List<Skill> findAllByOrderByUpdatedAtDesc();
}
