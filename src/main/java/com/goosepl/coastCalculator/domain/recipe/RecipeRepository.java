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

    @EntityGraph(attributePaths = {"user", "ingredients"})
    Optional<Recipe> findWithDetailsById(Long id);
}
