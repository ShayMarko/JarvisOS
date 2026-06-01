package com.jarvis.secrets;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SecretRepository extends JpaRepository<Secret, String> {

    List<Secret> findAllByOrderByNameAsc();
}
