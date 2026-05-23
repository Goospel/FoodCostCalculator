package com.goosepl.coastCalculator.domain.recipe;

import com.goosepl.coastCalculator.domain.user.Role;
import com.goosepl.coastCalculator.domain.user.User;
import com.goosepl.coastCalculator.domain.user.UserRepository;
import com.goosepl.coastCalculator.storage.ImageStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * RecipeService의 소유권 체크 / 미존재 분기에 대한 단위 테스트.
 *
 * 핵심:
 *  - findMine: 본인 레시피 → 성공, 타인 레시피 → AccessDeniedException, 미존재 → IllegalArgumentException
 *  - delete / update가 내부적으로 findMine을 호출하므로 같은 보호가 자동 적용됨 (간접 검증)
 */
@ExtendWith(MockitoExtension.class)
class RecipeServiceTest {

    @Mock
    private RecipeRepository recipeRepository;

    @Mock
    private UserRepository userRepository;

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
}
