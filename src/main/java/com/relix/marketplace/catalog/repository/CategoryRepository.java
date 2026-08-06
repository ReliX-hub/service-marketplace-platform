package com.relix.marketplace.catalog.repository;

import com.relix.marketplace.catalog.entity.Category;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByCode(String code);

    @EntityGraph(attributePaths = "parent")
    List<Category> findAllByOrderByNameAsc();
}
