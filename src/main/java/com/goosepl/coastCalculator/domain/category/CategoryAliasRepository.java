package com.goosepl.coastCalculator.domain.category;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryAliasRepository extends JpaRepository<CategoryAlias, Long> {

    Optional<CategoryAlias> findByAlias(String alias);

    boolean existsByAlias(String alias);

    /**
     * Admin 목록용 — alias 가나다순. canonical은 ManyToOne LAZY라 EntityGraph로 즉시 페치.
     * open-in-view: false 환경에서 템플릿 렌더링 시 LazyInit 방지.
     */
    @EntityGraph(attributePaths = "canonical")
    List<CategoryAlias> findAllByOrderByAliasAsc();
}
