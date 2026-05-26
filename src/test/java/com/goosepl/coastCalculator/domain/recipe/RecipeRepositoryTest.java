package com.goosepl.coastCalculator.domain.recipe;

import com.goosepl.coastCalculator.domain.ingredient.Unit;
import com.goosepl.coastCalculator.domain.user.Role;
import com.goosepl.coastCalculator.domain.user.User;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceUnit;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RecipeRepository의 two-step 쿼리 + EntityGraph 동작 검증.
 *
 * 목적:
 *  - **T2-11**: ToMany 컬렉션 페이징 시 in-memory paging 회피 — ID-only Page → IN 절 + EntityGraph fetch
 *  - **EntityGraph 설정 회귀 방지** — attributePath 오타/누락 시 즉시 감지
 *  - **N+1 회귀 방지** — Hibernate Statistics로 쿼리 수 측정
 *  - findWithDetailsById가 user/ingredients/ingredients.selectedIngredient까지 페치
 *  - findWithUserAndIngredientsById가 user/ingredients를 한 번에 페치 (findMine 패턴)
 */
@DataJpaTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
class RecipeRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private RecipeRepository recipeRepository;

    @PersistenceUnit
    private EntityManagerFactory emf;

    private Statistics statistics() {
        return emf.unwrap(SessionFactory.class).getStatistics();
    }

    private User persistUser(String username) {
        User user = User.builder()
                .username(username)
                .password("$2a$10$dummybcrypt")
                .role(Role.USER)
                .build();
        return em.persistAndFlush(user);
    }

    private Recipe persistRecipe(User owner, String name, String... categories) {
        Recipe recipe = Recipe.builder()
                .user(owner)
                .name(name)
                .servings(2)
                .build();
        int order = 0;
        for (String category : categories) {
            RecipeIngredient ingredient = RecipeIngredient.builder()
                    .categoryName(category)
                    .amount(new BigDecimal("100"))
                    .unit(Unit.G)
                    .ordering(order++)
                    .build();
            recipe.addIngredient(ingredient);
        }
        return em.persistAndFlush(recipe);
    }

    @BeforeEach
    void clearStats() {
        statistics().clear();
    }

    // ============================================================
    // 단건 조회 — EntityGraph 동작
    // ============================================================

    @Test
    @DisplayName("findWithDetailsById: user/ingredients/ingredients.selectedIngredient EntityGraph")
    void findWithDetailsByIdEagerLoadsUserAndIngredients() {
        User user = persistUser("alice");
        Recipe recipe = persistRecipe(user, "김치찌개", "김치", "돼지고기", "두부");
        em.clear();
        statistics().clear();

        Optional<Recipe> result = recipeRepository.findWithDetailsById(recipe.getId());

        assertThat(result).isPresent();
        Recipe loaded = result.get();
        assertThat(loaded.getName()).isEqualTo("김치찌개");
        assertThat(loaded.getUser().getUsername()).isEqualTo("alice");
        assertThat(loaded.getIngredients()).hasSize(3);
        assertThat(loaded.getIngredients())
                .extracting(RecipeIngredient::getCategoryName)
                .containsExactly("김치", "돼지고기", "두부");

        long executedQueries = statistics().getPrepareStatementCount();
        assertThat(executedQueries)
                .as("EntityGraph가 user/ingredients/selectedIngredient를 함께 페치하면 적은 쿼리로 끝나야 함")
                .isLessThanOrEqualTo(3);
    }

    @Test
    @DisplayName("findWithUserAndIngredientsById (findMine 패턴): user/ingredients만 페치, selectedIngredient는 LAZY")
    void findWithUserAndIngredientsByIdEagerLoadsTwoPaths() {
        User user = persistUser("alice");
        Recipe recipe = persistRecipe(user, "김치찌개", "김치", "돼지고기");
        em.clear();
        statistics().clear();

        Optional<Recipe> result = recipeRepository.findWithUserAndIngredientsById(recipe.getId());

        assertThat(result).isPresent();
        Recipe loaded = result.get();
        // user는 EntityGraph로 즉시 접근 가능
        assertThat(loaded.getUser().getUsername()).isEqualTo("alice");
        // ingredients도 즉시 접근 가능 (findMine에서 .size() 강제 초기화 제거 가능)
        assertThat(loaded.getIngredients()).hasSize(2);

        long executedQueries = statistics().getPrepareStatementCount();
        assertThat(executedQueries)
                .as("user + ingredients EntityGraph로 페치하면 3 이하 쿼리")
                .isLessThanOrEqualTo(3);
    }

    // ============================================================
    // T2-11 two-step 쿼리 — ID Page → entity fetch
    // ============================================================

    @Test
    @DisplayName("findIdsAllByCreatedAtDesc: ID-only Page, count + ID select만 — collection fetch 없음")
    void findIdsAllByCreatedAtDescReturnsIdPage() {
        User user = persistUser("alice");
        persistRecipe(user, "레시피 A", "밀가루");
        persistRecipe(user, "레시피 B", "설탕");
        persistRecipe(user, "레시피 C", "소금", "후추");
        em.clear();
        statistics().clear();

        Page<Long> idPage = recipeRepository.findIdsAllByCreatedAtDesc(PageRequest.of(0, 10));

        assertThat(idPage.getContent()).hasSize(3);
        assertThat(idPage.getTotalElements()).isEqualTo(3);
        assertThat(idPage.getContent()).allMatch(id -> id != null);
        // ID-only 쿼리는 count + select id 두 개 정도
        long executedQueries = statistics().getPrepareStatementCount();
        assertThat(executedQueries)
                .as("ID-only Page는 count + select id로 2 쿼리만 (collection fetch 미발생)")
                .isLessThanOrEqualTo(2);
    }

    @Test
    @DisplayName("findIdsAllByCreatedAtDesc: 페이지 크기/넘어가는 페이지 메타데이터")
    void findIdsAllByCreatedAtDescPagingMeta() {
        User user = persistUser("alice");
        for (int i = 0; i < 5; i++) {
            persistRecipe(user, "레시피 " + i, "밀가루");
        }
        em.clear();

        Page<Long> page0 = recipeRepository.findIdsAllByCreatedAtDesc(PageRequest.of(0, 2));
        assertThat(page0.getContent()).hasSize(2);
        assertThat(page0.getTotalElements()).isEqualTo(5);
        assertThat(page0.getTotalPages()).isEqualTo(3);
        assertThat(page0.hasNext()).isTrue();
        assertThat(page0.hasPrevious()).isFalse();

        Page<Long> page2 = recipeRepository.findIdsAllByCreatedAtDesc(PageRequest.of(2, 2));
        assertThat(page2.getContent()).hasSize(1);
        assertThat(page2.hasNext()).isFalse();
        assertThat(page2.hasPrevious()).isTrue();
    }

    @Test
    @DisplayName("findAllWithDetailsByIdInOrderByCreatedAtDesc: IN 절로 fetch + ORDER BY 보존 + EntityGraph")
    void findAllWithDetailsByIdInOrderByCreatedAtDescFetches() {
        User userA = persistUser("alice");
        User userB = persistUser("bob");
        Recipe r1 = persistRecipe(userA, "1번", "밀가루");
        Recipe r2 = persistRecipe(userB, "2번", "설탕");
        Recipe r3 = persistRecipe(userA, "3번", "소금", "후추");
        em.clear();
        statistics().clear();

        List<Recipe> fetched = recipeRepository
                .findAllWithDetailsByIdInOrderByCreatedAtDesc(List.of(r1.getId(), r2.getId(), r3.getId()));

        // ORDER BY r.createdAt DESC → 가장 늦게 만든 r3가 먼저
        assertThat(fetched).extracting(Recipe::getName).containsExactly("3번", "2번", "1번");
        // EntityGraph 적용 — user / ingredients 즉시 접근 가능
        assertThat(fetched).allMatch(r -> r.getUser().getUsername() != null);
        assertThat(fetched).allMatch(r -> r.getIngredients() != null && !r.getIngredients().isEmpty());

        // EntityGraph로 묶어서 페치 → 적은 쿼리
        long executedQueries = statistics().getPrepareStatementCount();
        assertThat(executedQueries)
                .as("IN 절 + EntityGraph는 LEFT JOIN으로 묶여 적은 쿼리")
                .isLessThanOrEqualTo(2);
    }

    @Test
    @DisplayName("findIdsByNameContainingIgnoreCase: 부분 일치 + 대소문자 무시 + ID Page")
    void findIdsByNameContainingIgnoreCase() {
        User user = persistUser("alice");
        persistRecipe(user, "김치찌개", "김치");
        persistRecipe(user, "된장찌개", "된장");
        persistRecipe(user, "TOMATO PASTA", "토마토");
        em.clear();

        Page<Long> kimchi = recipeRepository.findIdsByNameContainingIgnoreCaseOrderByCreatedAtDesc(
                "김치", PageRequest.of(0, 10));
        assertThat(kimchi.getTotalElements()).isEqualTo(1);

        Page<Long> jjigae = recipeRepository.findIdsByNameContainingIgnoreCaseOrderByCreatedAtDesc(
                "찌개", PageRequest.of(0, 10));
        assertThat(jjigae.getTotalElements()).isEqualTo(2);

        // 대소문자 무시
        Page<Long> caseInsensitive = recipeRepository.findIdsByNameContainingIgnoreCaseOrderByCreatedAtDesc(
                "tomato", PageRequest.of(0, 10));
        assertThat(caseInsensitive.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("findIdsByUserOrderByUpdatedAtDesc: 본인 ID만 + ID Page")
    void findIdsByUserOrderByUpdatedAtDesc() {
        User alice = persistUser("alice");
        User bob = persistUser("bob");
        persistRecipe(alice, "alice 1", "밀가루");
        persistRecipe(bob, "bob 것", "설탕");
        persistRecipe(alice, "alice 2", "소금");
        em.clear();

        Page<Long> alicePage = recipeRepository.findIdsByUserOrderByUpdatedAtDesc(alice, PageRequest.of(0, 10));
        assertThat(alicePage.getTotalElements()).isEqualTo(2);

        // bob 결과로 fetch해도 alice 것이 안 섞임
        Page<Long> bobPage = recipeRepository.findIdsByUserOrderByUpdatedAtDesc(bob, PageRequest.of(0, 10));
        assertThat(bobPage.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("findAllWithDetailsByIdInOrderByUpdatedAtDesc: updatedAt 정렬 보존")
    void findAllWithDetailsByIdInOrderByUpdatedAtDesc() {
        User user = persistUser("alice");
        Recipe r1 = persistRecipe(user, "1번", "밀가루");
        Recipe r2 = persistRecipe(user, "2번", "설탕");
        em.clear();

        List<Recipe> fetched = recipeRepository
                .findAllWithDetailsByIdInOrderByUpdatedAtDesc(List.of(r1.getId(), r2.getId()));

        // updatedAt DESC → 늦게 만든 r2가 먼저 (persistAndFlush가 updatedAt 갱신)
        assertThat(fetched).extracting(Recipe::getName).containsExactly("2번", "1번");
        // EntityGraph 적용
        assertThat(fetched).allMatch(r -> r.getUser().getUsername().equals("alice"));
        assertThat(fetched).allMatch(r -> !r.getIngredients().isEmpty());
    }

    // ============================================================
    // T2-12: Optimistic Locking (@Version)
    // ============================================================

    @Test
    @DisplayName("@Version: 신규 persist 시 version=0으로 초기화")
    void newRecipeStartsWithVersionZero() {
        User user = persistUser("alice");
        Recipe recipe = persistRecipe(user, "쿠키", "밀가루");

        assertThat(recipe.getVersion()).isEqualTo(0L);
    }

    @Test
    @DisplayName("@Version: 수정 후 flush 시 version +1 증가")
    void versionIncrementsOnUpdate() {
        User user = persistUser("alice");
        Recipe recipe = persistRecipe(user, "쿠키", "밀가루");
        Long id = recipe.getId();
        em.clear();

        Recipe loaded = em.find(Recipe.class, id);
        assertThat(loaded.getVersion()).isEqualTo(0L);
        loaded.update("쿠키 v2", 3);
        em.flush();

        assertThat(loaded.getVersion()).isEqualTo(1L);
    }

    @Test
    @DisplayName("@Version: 두 인스턴스가 같은 row를 따로 수정 → 두 번째 save가 OptimisticLockingFailureException")
    void optimisticLockingDetectsConflict() {
        // given: alice가 쿠키 레시피 보유 (v=0)
        User user = persistUser("alice");
        Recipe recipe = persistRecipe(user, "쿠키", "밀가루");
        Long id = recipe.getId();
        em.clear();

        // session A: stale 복사본 로드 (v=0) → detach
        Recipe stale = em.find(Recipe.class, id);
        em.detach(stale);

        // session B: 영속 상태로 로드 (v=0) → 수정 → flush → DB v=1
        Recipe fresh = em.find(Recipe.class, id);
        fresh.update("쿠키 v2", 3);
        em.flush();
        em.clear();

        // session A의 stale을 saveAndFlush 시도 → Spring이 OptimisticLockException을
        // ObjectOptimisticLockingFailureException으로 변환 (production 흐름과 동일)
        stale.update("쿠키 v3", 4);
        assertThatThrownBy(() -> recipeRepository.saveAndFlush(stale))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }
}
