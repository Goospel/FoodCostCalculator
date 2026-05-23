package com.goosepl.coastCalculator.domain.recipe;

import com.goosepl.coastCalculator.domain.user.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {

    @EntityGraph(attributePaths = {"ingredients"})
    List<Recipe> findByUserOrderByUpdatedAtDesc(User user);

    @EntityGraph(attributePaths = {"user", "ingredients"})
    List<Recipe> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"user", "ingredients"})
    List<Recipe> findByNameContainingIgnoreCaseOrderByCreatedAtDesc(String keyword, Pageable pageable);

    // T3-17: detail.html에서 ri.selectedIngredient.title 접근 → ingredients.selectedIngredient 까지 페치 필요
    // (open-in-view: false라 트랜잭션 밖 LazyInit 방지)
    @EntityGraph(attributePaths = {"user", "ingredients", "ingredients.selectedIngredient"})
    Optional<Recipe> findWithDetailsById(Long id);
}
