package com.goosepl.coastCalculator.domain.category;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByName(String name);

    boolean existsByName(String name);

    // 폼 datalist용 — 가나다순으로 정렬 (사용자 친화)
    List<Category> findAllByOrderByNameAsc();
}
