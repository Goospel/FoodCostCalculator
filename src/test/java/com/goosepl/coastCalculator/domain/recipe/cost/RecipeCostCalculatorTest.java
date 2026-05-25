package com.goosepl.coastCalculator.domain.recipe.cost;

import com.goosepl.coastCalculator.domain.category.CategoryAliasService;
import com.goosepl.coastCalculator.domain.ingredient.Ingredient;
import com.goosepl.coastCalculator.domain.ingredient.IngredientRepository;
import com.goosepl.coastCalculator.domain.ingredient.Unit;
import com.goosepl.coastCalculator.domain.recipe.Recipe;
import com.goosepl.coastCalculator.domain.recipe.RecipeIngredient;
import com.goosepl.coastCalculator.domain.user.Role;
import com.goosepl.coastCalculator.domain.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * T3-18.2: RecipeCostCalculator의 alias 풀이 통합 검증.
 *
 * - 행 카테고리가 alias("박력분")여도 canonical("밀가루")로 풀려서 ingredient 매칭됨
 * - selectedIngredient가 set된 행은 alias 풀이 경로를 타지 않음 (기존 정책 유지)
 */
@ExtendWith(MockitoExtension.class)
class RecipeCostCalculatorTest {

    @Mock
    private IngredientRepository ingredientRepository;

    @Mock
    private CategoryAliasService categoryAliasService;

    @InjectMocks
    private RecipeCostCalculator calculator;

    @Test
    @DisplayName("행 카테고리가 alias면 resolve된 canonical로 ingredient 매칭")
    void resolvesAliasBeforeMatching() {
        // given
        User alice = User.builder().username("alice").password("$2a$pw").role(Role.USER).build();
        Recipe recipe = Recipe.builder().user(alice).name("쿠키").servings(2).build();
        recipe.addIngredient(RecipeIngredient.builder()
                .categoryName("박력분") // 사용자가 alias 입력
                .amount(BigDecimal.valueOf(200))
                .unit(Unit.G)
                .ordering(0)
                .build());

        Ingredient flour = Ingredient.builder()
                .naverProductId("naver-1")
                .title("밀가루 1kg")
                .category("밀가루")
                .price(2000)
                .totalAmount(BigDecimal.valueOf(1000))
                .unit(Unit.G)
                .mallName("mall")
                .fetchedAt(LocalDateTime.now())
                .build();
        // pricePerGram = 2.0 (price/totalAmount, scale 4)
        given(categoryAliasService.resolve("박력분")).willReturn("밀가루");
        given(ingredientRepository.findByCategoryAndUnit("밀가루", Unit.G))
                .willReturn(List.of(flour));

        // when
        RecipeCostResult result = calculator.calculate(recipe, PricingPolicy.LOWEST);

        // then
        assertThat(result.lines()).hasSize(1);
        RecipeCostResult.IngredientCostLine line = result.lines().get(0);
        assertThat(line.missing()).isFalse();
        assertThat(line.matchedCandidateCount()).isEqualTo(1);
        // 화면 표시는 사용자 입력(박력분) 그대로 — 의도 보존
        assertThat(line.categoryName()).isEqualTo("박력분");
        assertThat(result.missingCount()).isZero();
    }

    @Test
    @DisplayName("alias 풀이 결과로도 매칭이 없으면 missing 처리")
    void missingWhenNoCandidateAfterResolve() {
        User alice = User.builder().username("alice").password("$2a$pw").role(Role.USER).build();
        Recipe recipe = Recipe.builder().user(alice).name("쿠키").servings(1).build();
        recipe.addIngredient(RecipeIngredient.builder()
                .categoryName("미지의재료")
                .amount(BigDecimal.valueOf(50))
                .unit(Unit.G)
                .ordering(0)
                .build());

        given(categoryAliasService.resolve("미지의재료")).willReturn("미지의재료");
        given(ingredientRepository.findByCategoryAndUnit("미지의재료", Unit.G))
                .willReturn(List.of());

        RecipeCostResult result = calculator.calculate(recipe, PricingPolicy.LOWEST);

        assertThat(result.lines()).hasSize(1);
        assertThat(result.lines().get(0).missing()).isTrue();
        assertThat(result.missingCount()).isEqualTo(1);
        assertThat(result.totalCost()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("selectedIngredient 행은 alias 풀이 경로를 타지 않음 (제품 단가 그대로)")
    void selectedIngredientBypassesAliasResolve() {
        User alice = User.builder().username("alice").password("$2a$pw").role(Role.USER).build();
        Recipe recipe = Recipe.builder().user(alice).name("쿠키").servings(1).build();

        Ingredient flour = Ingredient.builder()
                .naverProductId("naver-2")
                .title("밀가루 1kg")
                .category("밀가루")
                .price(1500)
                .totalAmount(BigDecimal.valueOf(1000))
                .unit(Unit.G)
                .mallName("mall")
                .fetchedAt(LocalDateTime.now())
                .build();

        recipe.addIngredient(RecipeIngredient.builder()
                .categoryName("박력분") // alias 이지만…
                .selectedIngredient(flour) // 명시 선택했으므로 alias 풀이 무관
                .amount(BigDecimal.valueOf(100))
                .unit(Unit.G)
                .ordering(0)
                .build());

        // when
        RecipeCostResult result = calculator.calculate(recipe, PricingPolicy.LOWEST);

        // then: aliasService도, ingredientRepository.findByCategoryAndUnit도 호출되지 않음
        verify(categoryAliasService, never()).resolve(any());
        verify(ingredientRepository, never()).findByCategoryAndUnit(any(), any());
        assertThat(result.lines()).hasSize(1);
        assertThat(result.lines().get(0).matchedCandidateCount()).isEqualTo(1);
    }
}
