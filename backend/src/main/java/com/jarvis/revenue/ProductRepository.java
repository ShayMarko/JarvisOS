package com.jarvis.revenue;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, String> {

    Optional<Product> findFirstByNameIgnoreCaseOrderByCreatedAtDesc(String name);

    List<Product> findAllByOrderByUpdatedAtDesc();
}
