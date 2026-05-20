package com.goosepl.coastCalculator.domain.recipe;

import com.goosepl.coastCalculator.domain.user.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {

    @EntityGraph(attributePaths = {"ingredients"})
    List<Recipe> findByUserOrderByUpdatedAtDesc(User user);
}
