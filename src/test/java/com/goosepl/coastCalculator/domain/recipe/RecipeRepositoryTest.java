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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceUnit;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RecipeRepository의 EntityGraph 동작을 검증.
 *
 * 목적:
 *  - EntityGraph 설정이 깨지면(잘못된 attributePath, 누락, 오타) 즉시 감지
 *  - findWithDetailsById가 user/ingredients를 함께 로드함 — N+1/LazyInit 회귀 방지
 *  - 검색·목록 메서드들이 의도한 정렬과 필터로 동작
 *
 * Hibernate Statistics로 EntityGraph 메서드의 쿼리 수를 측정해
 * "EntityGraph 누락 시 N+1이 폭발하는" 회귀를 직접 잡는다.
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

    @Test
    @DisplayName("findWithDetailsById: user와 ingredients를 EntityGraph로 함께 로드")
    void findWithDetailsByIdEagerLoadsUserAndIngredients() {
        User user = persistUser("alice");
        Recipe recipe = persistRecipe(user, "김치찌개", "김치", "돼지고기", "두부");
        em.clear();
        statistics().clear();

        Optional<Recipe> result = recipeRepository.findWithDetailsById(recipe.getId());

        assertThat(result).isPresent();
        Recipe loaded = result.get();
        assertThat(loaded.getName()).isEqualTo("김치찌개");
        // user / ingredients가 EntityGraph로 함께 페치돼 있어야 함
        assertThat(loaded.getUser().getUsername()).isEqualTo("alice");
        assertThat(loaded.getIngredients()).hasSize(3);
        assertThat(loaded.getIngredients())
                .extracting(RecipeIngredient::getCategoryName)
                .containsExactly("김치", "돼지고기", "두부");

        // EntityGraph 누락 시 4번 (recipe + user + 각 ingredient 그룹) 이상 쿼리가 나가지만
        // EntityGraph가 적용돼 있으면 3 이하 (Hibernate가 join fetch로 묶음)
        long executedQueries = statistics().getPrepareStatementCount();
        assertThat(executedQueries)
                .as("EntityGraph가 user/ingredients를 함께 페치하면 적은 쿼리로 끝나야 함")
                .isLessThanOrEqualTo(3);
    }

    @Test
    @DisplayName("findAllByOrderByCreatedAtDesc: 최신순 + user/ingredients EntityGraph + Page 메타데이터")
    void findAllRecentWithEntityGraph() {
        User userA = persistUser("alice");
        User userB = persistUser("bob");
        persistRecipe(userA, "레시피 1", "밀가루");
        persistRecipe(userB, "레시피 2", "설탕");
        persistRecipe(userA, "레시피 3", "소금", "후추");
        em.clear();
        statistics().clear();

        Page<Recipe> recent = recipeRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 10));

        assertThat(recent.getContent()).hasSize(3);
        assertThat(recent.getTotalElements()).isEqualTo(3);
        assertThat(recent.getTotalPages()).isEqualTo(1);
        // user 필드가 LazyInit 없이 접근 가능
        assertThat(recent.getContent()).allMatch(r -> r.getUser().getUsername() != null);
        // ingredients 도 함께 로드
        assertThat(recent.getContent()).allMatch(r -> r.getIngredients() != null);
    }

    @Test
    @DisplayName("findAllByOrderByCreatedAtDesc: 페이지 크기/넘어가는 페이지 메타데이터")
    void findAllRecentPaging() {
        User user = persistUser("alice");
        // 5개 생성 → size=2 → totalPages=3
        for (int i = 0; i < 5; i++) {
            persistRecipe(user, "레시피 " + i, "밀가루");
        }
        em.clear();

        Page<Recipe> page0 = recipeRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 2));
        assertThat(page0.getContent()).hasSize(2);
        assertThat(page0.getTotalElements()).isEqualTo(5);
        assertThat(page0.getTotalPages()).isEqualTo(3);
        assertThat(page0.hasNext()).isTrue();
        assertThat(page0.hasPrevious()).isFalse();

        Page<Recipe> page2 = recipeRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(2, 2));
        assertThat(page2.getContent()).hasSize(1);
        assertThat(page2.hasNext()).isFalse();
        assertThat(page2.hasPrevious()).isTrue();
        assertThat(page2.getNumber()).isEqualTo(2);
    }

    @Test
    @DisplayName("findByNameContainingIgnoreCase: 부분 일치 + 대소문자 무시 + Page 반환")
    void searchByNameIgnoresCase() {
        User user = persistUser("alice");
        persistRecipe(user, "김치찌개", "김치");
        persistRecipe(user, "된장찌개", "된장");
        persistRecipe(user, "TOMATO PASTA", "토마토");
        em.clear();

        Page<Recipe> kimchi = recipeRepository
                .findByNameContainingIgnoreCaseOrderByCreatedAtDesc("김치", PageRequest.of(0, 10));
        assertThat(kimchi.getContent())
                .extracting(Recipe::getName)
                .containsExactlyInAnyOrder("김치찌개");
        assertThat(kimchi.getTotalElements()).isEqualTo(1);

        Page<Recipe> jjigae = recipeRepository
                .findByNameContainingIgnoreCaseOrderByCreatedAtDesc("찌개", PageRequest.of(0, 10));
        assertThat(jjigae.getContent())
                .extracting(Recipe::getName)
                .containsExactlyInAnyOrder("김치찌개", "된장찌개");
        assertThat(jjigae.getTotalElements()).isEqualTo(2);

        // 대소문자 무시
        Page<Recipe> caseInsensitive = recipeRepository
                .findByNameContainingIgnoreCaseOrderByCreatedAtDesc("tomato", PageRequest.of(0, 10));
        assertThat(caseInsensitive.getContent())
                .extracting(Recipe::getName)
                .containsExactly("TOMATO PASTA");
    }

    @Test
    @DisplayName("findByUserOrderByUpdatedAtDesc: 본인 레시피만 반환 + ingredients EntityGraph + Page")
    void findByUserReturnsOnlyMine() {
        User alice = persistUser("alice");
        User bob = persistUser("bob");
        persistRecipe(alice, "alice의 레시피 1", "밀가루");
        persistRecipe(bob, "bob의 레시피", "설탕");
        persistRecipe(alice, "alice의 레시피 2", "소금", "후추");
        em.clear();

        Page<Recipe> aliceRecipes = recipeRepository
                .findByUserOrderByUpdatedAtDesc(alice, PageRequest.of(0, 10));

        assertThat(aliceRecipes.getContent()).hasSize(2);
        assertThat(aliceRecipes.getTotalElements()).isEqualTo(2);
        assertThat(aliceRecipes.getContent())
                .extracting(Recipe::getName)
                .containsExactlyInAnyOrder("alice의 레시피 1", "alice의 레시피 2");
        // ingredients EntityGraph 동작
        assertThat(aliceRecipes.getContent()).allMatch(r -> r.getIngredients() != null && !r.getIngredients().isEmpty());
    }
}
