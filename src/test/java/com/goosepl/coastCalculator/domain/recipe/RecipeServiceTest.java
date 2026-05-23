package com.goosepl.coastCalculator.domain.recipe;

import com.goosepl.coastCalculator.domain.ingredient.Ingredient;
import com.goosepl.coastCalculator.domain.ingredient.IngredientRepository;
import com.goosepl.coastCalculator.domain.ingredient.Unit;
import com.goosepl.coastCalculator.domain.recipe.dto.RecipeForm;
import com.goosepl.coastCalculator.domain.recipe.dto.RecipeIngredientForm;
import com.goosepl.coastCalculator.domain.user.Role;
import com.goosepl.coastCalculator.domain.user.User;
import com.goosepl.coastCalculator.domain.user.UserRepository;
import com.goosepl.coastCalculator.storage.ImageStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * RecipeService의 소유권 체크 / 미존재 분기 + T3-17 selectedIngredient 검증 단위 테스트.
 */
@ExtendWith(MockitoExtension.class)
class RecipeServiceTest {

    @Mock
    private RecipeRepository recipeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private IngredientRepository ingredientRepository;

    @Mock
    private ImageStorageService imageStorageService;

    @InjectMocks
    private RecipeService recipeService;

    private User alice;
    private User bob;
    private Recipe aliceRecipe;

    @BeforeEach
    void setUp() {
        alice = User.builder()
                .username("alice")
                .password("$2a$10$dummy")
                .role(Role.USER)
                .build();
        bob = User.builder()
                .username("bob")
                .password("$2a$10$dummy")
                .role(Role.USER)
                .build();
        aliceRecipe = Recipe.builder()
                .user(alice)
                .name("alice의 레시피")
                .servings(2)
                .build();
    }

    @Test
    @DisplayName("findMine: 본인이 만든 레시피는 정상 반환")
    void findMineReturnsRecipeWhenOwner() {
        given(recipeRepository.findById(1L)).willReturn(Optional.of(aliceRecipe));

        Recipe result = recipeService.findMine(1L, "alice");

        assertThat(result).isSameAs(aliceRecipe);
    }

    @Test
    @DisplayName("findMine: 타인의 레시피 접근 시 AccessDeniedException")
    void findMineThrowsWhenNotOwner() {
        given(recipeRepository.findById(1L)).willReturn(Optional.of(aliceRecipe));

        assertThatThrownBy(() -> recipeService.findMine(1L, "bob"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("권한");
    }

    @Test
    @DisplayName("findMine: 존재하지 않는 ID 접근 시 IllegalArgumentException")
    void findMineThrowsWhenNotFound() {
        given(recipeRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> recipeService.findMine(999L, "alice"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("레시피를 찾을 수 없습니다");
    }

    @Test
    @DisplayName("findForView: 소유자 체크 없이 누구나 조회 가능 (공개 페이지)")
    void findForViewIsPublic() {
        given(recipeRepository.findWithDetailsById(1L)).willReturn(Optional.of(aliceRecipe));

        // 본인 username과 무관하게 호출 가능 — 익명/타인 모두 조회 가능
        Recipe result = recipeService.findForView(1L);

        assertThat(result).isSameAs(aliceRecipe);
    }

    @Test
    @DisplayName("findForView: 미존재 시 IllegalArgumentException")
    void findForViewThrowsWhenNotFound() {
        given(recipeRepository.findWithDetailsById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> recipeService.findForView(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("레시피를 찾을 수 없습니다");
    }

    @Test
    @DisplayName("delete: 본인이 아니면 AccessDeniedException (findMine 보호가 자동 적용)")
    void deleteByNonOwnerIsRejected() {
        given(recipeRepository.findById(1L)).willReturn(Optional.of(aliceRecipe));

        assertThatThrownBy(() -> recipeService.delete(1L, "bob"))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ---- T3-17: selectedIngredient 분기 ----

    @Test
    @DisplayName("create: selectedIngredientId 지정 → 카테고리/단위 일치 시 RecipeIngredient.selectedIngredient에 set")
    void createBindsSelectedIngredientWhenMatching() {
        // given
        Ingredient flour = newIngredient(7L, "밀가루", Unit.G, 1000, BigDecimal.valueOf(500));
        given(userRepository.findByUsername("alice")).willReturn(Optional.of(alice));
        given(ingredientRepository.findById(7L)).willReturn(Optional.of(flour));
        ArgumentCaptor<Recipe> savedRecipe = ArgumentCaptor.forClass(Recipe.class);
        given(recipeRepository.save(any(Recipe.class))).willAnswer(inv -> inv.getArgument(0));

        RecipeForm form = makeForm("쿠키", 2,
                row("밀가루", BigDecimal.valueOf(200), Unit.G, 7L));

        // when
        recipeService.create(form, "alice", null);

        // then
        org.mockito.Mockito.verify(recipeRepository).save(savedRecipe.capture());
        Recipe captured = savedRecipe.getValue();
        assertThat(captured.getIngredients()).hasSize(1);
        RecipeIngredient ri = captured.getIngredients().getFirst();
        assertThat(ri.getSelectedIngredient()).isNotNull();
        assertThat(ri.getSelectedIngredient().getId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("create: selectedIngredient의 단위 불일치 → IllegalArgumentException으로 저장 거부")
    void createRejectsUnitMismatch() {
        Ingredient soySauceMl = newIngredient(8L, "간장", Unit.ML, 500, BigDecimal.valueOf(1000));
        given(userRepository.findByUsername("alice")).willReturn(Optional.of(alice));
        given(ingredientRepository.findById(8L)).willReturn(Optional.of(soySauceMl));

        RecipeForm form = makeForm("조림", 2,
                row("간장", BigDecimal.valueOf(50), Unit.G, 8L)); // 행은 G, 제품은 ML

        assertThatThrownBy(() -> recipeService.create(form, "alice", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("단위");

        org.mockito.Mockito.verify(recipeRepository, org.mockito.Mockito.never()).save(any(Recipe.class));
    }

    @Test
    @DisplayName("create: selectedIngredient의 카테고리 불일치 → IllegalArgumentException으로 저장 거부")
    void createRejectsCategoryMismatch() {
        Ingredient sugar = newIngredient(9L, "설탕", Unit.G, 2000, BigDecimal.valueOf(1000));
        given(userRepository.findByUsername("alice")).willReturn(Optional.of(alice));
        given(ingredientRepository.findById(9L)).willReturn(Optional.of(sugar));

        // 행 카테고리는 "밀가루"인데 선택한 제품은 카테고리 "설탕"
        RecipeForm form = makeForm("쿠키", 2,
                row("밀가루", BigDecimal.valueOf(200), Unit.G, 9L));

        assertThatThrownBy(() -> recipeService.create(form, "alice", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("카테고리");

        org.mockito.Mockito.verify(recipeRepository, org.mockito.Mockito.never()).save(any(Recipe.class));
    }

    @Test
    @DisplayName("create: selectedIngredientId 미지정 행은 RecipeIngredient.selectedIngredient = null")
    void createLeavesSelectedNullWhenIdAbsent() {
        given(userRepository.findByUsername("alice")).willReturn(Optional.of(alice));
        ArgumentCaptor<Recipe> savedRecipe = ArgumentCaptor.forClass(Recipe.class);
        given(recipeRepository.save(any(Recipe.class))).willAnswer(inv -> inv.getArgument(0));

        RecipeForm form = makeForm("쿠키", 2,
                row("밀가루", BigDecimal.valueOf(200), Unit.G, null));

        recipeService.create(form, "alice", null);

        org.mockito.Mockito.verify(recipeRepository).save(savedRecipe.capture());
        RecipeIngredient ri = savedRecipe.getValue().getIngredients().getFirst();
        assertThat(ri.getSelectedIngredient()).isNull();
        // ingredientRepository는 호출되지 않아야 함
        org.mockito.Mockito.verifyNoInteractions(ingredientRepository);
    }

    @Test
    @DisplayName("create: selectedIngredientId가 존재하지 않는 ID → IllegalArgumentException")
    void createRejectsUnknownIngredientId() {
        given(userRepository.findByUsername("alice")).willReturn(Optional.of(alice));
        given(ingredientRepository.findById(404L)).willReturn(Optional.empty());

        RecipeForm form = makeForm("쿠키", 2,
                row("밀가루", BigDecimal.valueOf(200), Unit.G, 404L));

        assertThatThrownBy(() -> recipeService.create(form, "alice", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("선택한 제품을 찾을 수 없습니다");
    }

    // ---- helpers ----

    private RecipeForm makeForm(String name, int servings, RecipeIngredientForm... rows) {
        RecipeForm f = new RecipeForm();
        f.setName(name);
        f.setServings(servings);
        for (RecipeIngredientForm r : rows) {
            f.getIngredients().add(r);
        }
        return f;
    }

    private RecipeIngredientForm row(String category, BigDecimal amount, Unit unit, Long selectedId) {
        RecipeIngredientForm r = new RecipeIngredientForm();
        r.setCategoryName(category);
        r.setAmount(amount);
        r.setUnit(unit);
        r.setSelectedIngredientId(selectedId);
        return r;
    }

    /**
     * Ingredient의 id는 @GeneratedValue라 빌더로는 못 세팅 → 리플렉션으로 주입.
     * 단위 테스트에서만 사용.
     */
    private Ingredient newIngredient(long id, String category, Unit unit, int price, BigDecimal totalAmount) {
        Ingredient ing = Ingredient.builder()
                .naverProductId("naver-" + id)
                .title(category + " 테스트제품")
                .category(category)
                .price(price)
                .totalAmount(totalAmount)
                .unit(unit)
                .image(null)
                .mallName("mall-" + id)
                .link(null)
                .fetchedAt(LocalDateTime.now())
                .build();
        try {
            Field idField = Ingredient.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(ing, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("test id injection failed", e);
        }
        return ing;
    }
}
