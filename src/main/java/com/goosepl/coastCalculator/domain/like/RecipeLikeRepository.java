package com.goosepl.coastCalculator.domain.like;

import com.goosepl.coastCalculator.domain.recipe.Recipe;
import com.goosepl.coastCalculator.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RecipeLikeRepository extends JpaRepository<RecipeLike, Long> {

    Optional<RecipeLike> findByRecipeAndUser(Recipe recipe, User user);

    boolean existsByRecipeAndUser(Recipe recipe, User user);

    long countByRecipe(Recipe recipe);

    long countByRecipeId(Long recipeId);

    boolean existsByRecipeIdAndUserUsername(Long recipeId, String username);
}
