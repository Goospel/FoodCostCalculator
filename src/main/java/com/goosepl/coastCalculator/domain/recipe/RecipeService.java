package com.goosepl.coastCalculator.domain.recipe;

import com.goosepl.coastCalculator.domain.recipe.dto.RecipeForm;
import com.goosepl.coastCalculator.domain.recipe.dto.RecipeIngredientForm;
import com.goosepl.coastCalculator.domain.user.User;
import com.goosepl.coastCalculator.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RecipeService {

    private final RecipeRepository recipeRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<Recipe> findMyRecipes(String username) {
        User user = loadUser(username);
        return recipeRepository.findByUserOrderByUpdatedAtDesc(user);
    }

    @Transactional(readOnly = true)
    public Recipe findMine(Long id, String username) {
        Recipe recipe = recipeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("레시피를 찾을 수 없습니다: id=" + id));
        if (!recipe.isOwnedBy(username)) {
            throw new AccessDeniedException("이 레시피에 접근할 권한이 없습니다");
        }
        // ingredients lazy 컬렉션을 트랜잭션 내에서 초기화
        recipe.getIngredients().size();
        return recipe;
    }

    @Transactional
    public Long create(RecipeForm form, String username) {
        User user = loadUser(username);
        Recipe recipe = Recipe.builder()
                .user(user)
                .name(form.getName().trim())
                .servings(form.getServings())
                .build();
        applyIngredients(recipe, form);
        return recipeRepository.save(recipe).getId();
    }

    @Transactional
    public void update(Long id, RecipeForm form, String username) {
        Recipe recipe = findMine(id, username);
        recipe.update(form.getName().trim(), form.getServings());
        recipe.clearIngredients();
        applyIngredients(recipe, form);
    }

    @Transactional
    public void delete(Long id, String username) {
        Recipe recipe = findMine(id, username);
        recipeRepository.delete(recipe);
    }

    private void applyIngredients(Recipe recipe, RecipeForm form) {
        int order = 0;
        for (RecipeIngredientForm row : form.getIngredients()) {
            if (row.isEmpty()) {
                continue;
            }
            if (row.getCategoryName() == null || row.getCategoryName().isBlank()) {
                throw new IllegalArgumentException("재료 카테고리를 입력해주세요");
            }
            if (row.getAmount() == null || row.getAmount().signum() <= 0) {
                throw new IllegalArgumentException("재료 양은 0보다 커야 합니다: " + row.getCategoryName());
            }
            if (row.getUnit() == null) {
                throw new IllegalArgumentException("재료 단위를 선택해주세요: " + row.getCategoryName());
            }
            RecipeIngredient ingredient = RecipeIngredient.builder()
                    .categoryName(row.getCategoryName().trim())
                    .amount(row.getAmount())
                    .unit(row.getUnit())
                    .ordering(order++)
                    .build();
            recipe.addIngredient(ingredient);
        }
    }

    private User loadUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("사용자를 찾을 수 없습니다: " + username));
    }
}
