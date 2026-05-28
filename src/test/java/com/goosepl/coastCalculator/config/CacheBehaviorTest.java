package com.goosepl.coastCalculator.config;

import com.goosepl.coastCalculator.domain.category.Category;
import com.goosepl.coastCalculator.domain.category.CategoryAlias;
import com.goosepl.coastCalculator.domain.category.CategoryAliasRepository;
import com.goosepl.coastCalculator.domain.category.CategoryAliasService;
import com.goosepl.coastCalculator.domain.category.CategoryRepository;
import com.goosepl.coastCalculator.domain.category.CategoryService;
import com.goosepl.coastCalculator.domain.ingredient.Ingredient;
import com.goosepl.coastCalculator.domain.ingredient.IngredientRepository;
import com.goosepl.coastCalculator.domain.ingredient.IngredientService;
import com.goosepl.coastCalculator.domain.ingredient.Unit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * T2-10: Caffeine 캐시 동작/무효화 통합 테스트.
 *
 * 기존 application-test.yaml은 cache.type=none이라 캐시 비활성 → properties override로 caffeine 강제.
 * 한 컨텍스트에서 3개 캐시 모두 검증해 컨텍스트 캐시 효율 확보 (별도 클래스로 쪼개면 부팅 4번).
 *
 * Repository는 @MockitoBean으로 대체 — 서비스의 캐시 어노테이션이 Spring AOP 프록시로 동작하는지가 검증 대상.
 *
 * 각 @Nested 시작 전 모든 캐시 clear — 케이스 간 캐시 누수 방지.
 */
@SpringBootTest(properties = {
        "spring.cache.type=caffeine",
        "spring.cache.cache-names=categoryNames,categoryAliasMap,ingredientGroupsVisible",
        "spring.cache.caffeine.spec=maximumSize=2000,expireAfterWrite=30m"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CacheBehaviorTest {

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private CategoryAliasService categoryAliasService;

    @Autowired
    private IngredientService ingredientService;

    @MockitoBean
    private CategoryRepository categoryRepository;

    @MockitoBean
    private CategoryAliasRepository categoryAliasRepository;

    @MockitoBean
    private IngredientRepository ingredientRepository;

    @BeforeEach
    void clearAllCaches() {
        cacheManager.getCacheNames().forEach(name -> {
            var c = cacheManager.getCache(name);
            if (c != null) c.clear();
        });
    }

    @Test
    @DisplayName("CacheManager 빈: CaffeineCacheManager 등록 + 세 캐시 이름 모두 인지")
    void cacheManagerSmoke() {
        assertThat(cacheManager).isInstanceOf(CaffeineCacheManager.class);
        assertThat(cacheManager.getCacheNames())
                .contains("categoryNames", "categoryAliasMap", "ingredientGroupsVisible");
    }

    @Nested
    @DisplayName("CategoryService.findAllNames — 캐시 + ensureExists 무효화")
    class CategoryNamesCache {

        @Test
        @DisplayName("같은 인자 2회 호출 → repository는 1번만")
        void hitsCacheOnSecondCall() {
            given(categoryRepository.findAllByOrderByNameAsc())
                    .willReturn(List.of(Category.builder().name("밀가루").build()));

            categoryService.findAllNames();
            categoryService.findAllNames();

            verify(categoryRepository, times(1)).findAllByOrderByNameAsc();
        }

        @Test
        @DisplayName("ensureExists 새 카테고리 등록 → 캐시 무효화 → 다시 호출 시 repository 재호출")
        void ensureExistsEvictsCache() {
            given(categoryRepository.findAllByOrderByNameAsc()).willReturn(List.of());
            given(categoryRepository.findByName("새카테")).willReturn(Optional.empty());
            given(categoryRepository.save(any(Category.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            categoryService.findAllNames(); // 1차 — 캐시 적재
            categoryService.ensureExists("새카테"); // evict
            categoryService.findAllNames(); // 2차 — 다시 DB

            verify(categoryRepository, times(2)).findAllByOrderByNameAsc();
        }
    }

    @Nested
    @DisplayName("CategoryAliasService.resolve — 캐시 + add/delete 무효화")
    class CategoryAliasCache {

        @Test
        @DisplayName("같은 input 2회 → repository 호출 1번만")
        void resolveHitsCacheOnSecondCall() {
            given(categoryRepository.existsByName("박력분")).willReturn(false);
            given(categoryAliasRepository.findByAlias("박력분")).willReturn(Optional.empty());

            categoryAliasService.resolve("박력분");
            categoryAliasService.resolve("박력분");

            verify(categoryAliasRepository, times(1)).findByAlias("박력분");
        }

        @Test
        @DisplayName("add 호출 후 resolve 캐시 무효화 → repository 재호출")
        void addEvictsResolveCache() {
            Category canonical = Category.builder().name("밀가루").build();
            given(categoryRepository.existsByName("박력분")).willReturn(false);
            given(categoryRepository.existsByName("밀가루")).willReturn(true);
            given(categoryAliasRepository.findByAlias("박력분")).willReturn(Optional.empty());
            given(categoryAliasRepository.existsByAlias("박력분")).willReturn(false);
            given(categoryRepository.findByName("밀가루")).willReturn(Optional.of(canonical));
            given(categoryAliasRepository.save(any(CategoryAlias.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            categoryAliasService.resolve("박력분"); // 1차
            categoryAliasService.add("박력분", "밀가루"); // evict
            categoryAliasService.resolve("박력분"); // 2차 — 캐시 비어 있음

            verify(categoryAliasRepository, times(2)).findByAlias("박력분");
        }

        @Test
        @DisplayName("null/blank input은 캐시 안 됨 (unless 조건)")
        void blankInputBypassesCache() {
            // null은 메서드 진입 즉시 null 반환, blank는 그대로 반환 — repository 호출 자체 없음.
            // 캐시에 들어가지 않음을 확인: 두 번째 호출도 메서드 본문 실행 (관찰 가능한 차이는 없으나
            // 캐시 어노테이션이 unless로 skip하는지가 핵심).
            categoryAliasService.resolve(null);
            categoryAliasService.resolve("   ");
            var cache = cacheManager.getCache("categoryAliasMap");
            assertThat(cache).isNotNull();
            // null/blank 키로 캐시 적재 시도가 unless로 막혔는지 — get으로 확인
            assertThat(cache.get("   ")).isNull();
        }
    }

    @Nested
    @DisplayName("IngredientService.findAllVisible — 캐시 + updateCategory/fetchAndUpsert/delete 무효화")
    class IngredientVisibleCache {

        @Test
        @DisplayName("같은 인자 2회 → DB 1번만")
        void hitsCacheOnSecondCall() {
            given(ingredientRepository.findByCategoryIsNotNullOrderByCategoryAscPricePerGramAsc())
                    .willReturn(List.of(sampleIngredient()));

            ingredientService.findAllVisible();
            ingredientService.findAllVisible();

            verify(ingredientRepository, times(1))
                    .findByCategoryIsNotNullOrderByCategoryAscPricePerGramAsc();
        }

        @Test
        @DisplayName("updateCategory 호출 → 캐시 무효화 → 재조회")
        void updateCategoryEvictsCache() {
            Ingredient ing = sampleIngredient();
            given(ingredientRepository.findByCategoryIsNotNullOrderByCategoryAscPricePerGramAsc())
                    .willReturn(List.of(ing));
            given(ingredientRepository.findById(1L)).willReturn(Optional.of(ing));
            given(categoryRepository.findByName("새카테")).willReturn(Optional.empty());
            given(categoryRepository.save(any(Category.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            ingredientService.findAllVisible(); // 1차
            ingredientService.updateCategory(1L, "새카테"); // evict
            ingredientService.findAllVisible(); // 2차

            verify(ingredientRepository, times(2))
                    .findByCategoryIsNotNullOrderByCategoryAscPricePerGramAsc();
        }
    }

    private static Ingredient sampleIngredient() {
        return Ingredient.builder()
                .naverProductId("P-1")
                .title("샘플 1kg")
                .category("밀가루")
                .price(3000)
                .totalAmount(new BigDecimal("1000"))
                .unit(Unit.G)
                .image("img.jpg")
                .mallName("몰")
                .link("link")
                .fetchedAt(LocalDateTime.now())
                .build();
    }
}
