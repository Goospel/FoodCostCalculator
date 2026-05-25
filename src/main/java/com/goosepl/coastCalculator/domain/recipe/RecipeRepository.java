package com.goosepl.coastCalculator.domain.recipe;

import com.goosepl.coastCalculator.domain.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {

    // T2-7: 내 레시피 목록도 페이징 — 본인 레시피가 많아질 때 무한 스크롤 방지
    @EntityGraph(attributePaths = {"ingredients"})
    Page<Recipe> findByUserOrderByUpdatedAtDesc(User user, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "ingredients"})
    Page<Recipe> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"user", "ingredients"})
    Page<Recipe> findByNameContainingIgnoreCaseOrderByCreatedAtDesc(String keyword, Pageable pageable);

    // T3-17: detail.html에서 ri.selectedIngredient.title 접근 → ingredients.selectedIngredient 까지 페치 필요
    // (open-in-view: false라 트랜잭션 밖 LazyInit 방지)
    @EntityGraph(attributePaths = {"user", "ingredients", "ingredients.selectedIngredient"})
    Optional<Recipe> findWithDetailsById(Long id);
}
