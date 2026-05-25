package com.goosepl.coastCalculator.domain.recipe;

import com.goosepl.coastCalculator.domain.ingredient.Ingredient;
import com.goosepl.coastCalculator.domain.ingredient.IngredientRepository;
import com.goosepl.coastCalculator.domain.recipe.dto.RecipeForm;
import com.goosepl.coastCalculator.domain.recipe.dto.RecipeIngredientForm;
import com.goosepl.coastCalculator.domain.user.User;
import com.goosepl.coastCalculator.domain.user.UserRepository;
import com.goosepl.coastCalculator.storage.ImageStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class RecipeService {

    private final RecipeRepository recipeRepository;
    private final UserRepository userRepository;
    private final IngredientRepository ingredientRepository;
    private final ImageStorageService imageStorageService;

    // T2-7: 내 레시피 목록 페이징
    @Transactional(readOnly = true)
    public Page<Recipe> findMyRecipes(String username, Pageable pageable) {
        User user = loadUser(username);
        return recipeRepository.findByUserOrderByUpdatedAtDesc(user, pageable);
    }

    // T2-7: 홈 페이지 최근 레시피 페이징
    @Transactional(readOnly = true)
    public Page<Recipe> findRecent(Pageable pageable) {
        return recipeRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    // T2-7: 검색 결과 페이징. blank 키워드는 findRecent와 동일 결과
    @Transactional(readOnly = true)
    public Page<Recipe> searchByName(String keyword, Pageable pageable) {
        if (keyword == null || keyword.isBlank()) {
            return findRecent(pageable);
        }
        return recipeRepository.findByNameContainingIgnoreCaseOrderByCreatedAtDesc(
                keyword.trim(), pageable);
    }

    @Transactional(readOnly = true)
    public Recipe findForView(Long id) {
        return recipeRepository.findWithDetailsById(id)
                .orElseThrow(() -> new IllegalArgumentException("레시피를 찾을 수 없습니다: id=" + id));
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
    public Long create(RecipeForm form, String username, MultipartFile image) {
        User user = loadUser(username);
        String imageUrl = (image != null && !image.isEmpty()) ? imageStorageService.save(image) : null;
        Recipe recipe = Recipe.builder()
                .user(user)
                .name(form.getName().trim())
                .servings(form.getServings())
                .imageUrl(imageUrl)
                .build();
        applyIngredients(recipe, form);
        return recipeRepository.save(recipe).getId();
    }

    @Transactional
    public void update(Long id, RecipeForm form, String username, MultipartFile image, boolean removeImage) {
        Recipe recipe = findMine(id, username);
        recipe.update(form.getName().trim(), form.getServings());

        if (removeImage) {
            imageStorageService.delete(recipe.getImageUrl());
            recipe.clearImage();
        } else if (image != null && !image.isEmpty()) {
            // 새 이미지 업로드 — 기존 파일은 디스크에서 정리
            String oldUrl = recipe.getImageUrl();
            String newUrl = imageStorageService.save(image);
            recipe.setImage(newUrl);
            imageStorageService.delete(oldUrl);
        }

        recipe.clearIngredients();
        applyIngredients(recipe, form);
    }

    @Transactional
    public void delete(Long id, String username) {
        Recipe recipe = findMine(id, username);
        String imageUrl = recipe.getImageUrl();
        recipeRepository.delete(recipe);
        // 트랜잭션 커밋과 무관하게 파일 시스템도 정리 (best-effort)
        imageStorageService.delete(imageUrl);
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
            String rowCategory = row.getCategoryName().trim();
            Ingredient selected = resolveSelectedIngredient(row.getSelectedIngredientId(), rowCategory, row.getUnit());
            RecipeIngredient ingredient = RecipeIngredient.builder()
                    .categoryName(rowCategory)
                    .selectedIngredient(selected)
                    .amount(row.getAmount())
                    .unit(row.getUnit())
                    .ordering(order++)
                    .build();
            recipe.addIngredient(ingredient);
        }
    }

    /**
     * T3-17: 사용자가 "이 제품으로 고정"으로 특정 Ingredient를 선택한 경우 검증 + 반환.
     * 카테고리(대소문자 무시)와 단위가 행과 일치해야 함. 불일치 시 IllegalArgumentException으로 저장 거부.
     */
    private Ingredient resolveSelectedIngredient(Long selectedId, String rowCategory, com.goosepl.coastCalculator.domain.ingredient.Unit rowUnit) {
        if (selectedId == null) {
            return null;
        }
        Ingredient selected = ingredientRepository.findById(selectedId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "선택한 제품을 찾을 수 없습니다 (id=" + selectedId + "): " + rowCategory));
        if (selected.getCategory() == null || selected.getCategory().isBlank()) {
            throw new IllegalArgumentException(
                    "선택한 제품(" + selected.getTitle() + ")은 카테고리가 부여되지 않아 사용할 수 없습니다");
        }
        if (!selected.getCategory().equalsIgnoreCase(rowCategory)) {
            throw new IllegalArgumentException(
                    "선택한 제품의 카테고리(" + selected.getCategory()
                            + ")가 입력 카테고리(" + rowCategory + ")와 다릅니다");
        }
        if (selected.getUnit() != rowUnit) {
            throw new IllegalArgumentException(
                    "선택한 제품의 단위(" + selected.getUnit()
                            + ")가 입력 단위(" + rowUnit + ")와 다릅니다: " + rowCategory);
        }
        return selected;
    }

    private User loadUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("사용자를 찾을 수 없습니다: " + username));
    }
}
