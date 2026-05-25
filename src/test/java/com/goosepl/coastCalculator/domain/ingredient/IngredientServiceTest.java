package com.goosepl.coastCalculator.domain.ingredient;

import com.goosepl.coastCalculator.config.NaverApiProperties;
import com.goosepl.coastCalculator.domain.category.CategoryService;
import com.goosepl.coastCalculator.external.naver.NaverShoppingClient;
import com.goosepl.coastCalculator.external.naver.dto.NaverProduct;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * IngredientService의 핵심 규칙 검증.
 *
 * 핵심 규칙 (CLAUDE.md):
 *  - Naver fetch upsert 시 기존 row의 category는 절대 건드리지 않는다 ← 가장 중요한 invariant
 *  - 신규 row insert 시 category=null
 *  - UnitParser 파싱 실패한 product는 skip
 */
@ExtendWith(MockitoExtension.class)
class IngredientServiceTest {

    @Mock
    private IngredientRepository ingredientRepository;

    @Mock
    private NaverShoppingClient naverClient;

    @Mock
    private NaverApiProperties naverApiProperties;

    @Mock
    private CategoryService categoryService;

    @Mock
    private IngredientRefetchService refetchService;

    @InjectMocks
    private IngredientService ingredientService;

    private static NaverProduct product(String id, String title, int price) {
        return new NaverProduct(id, title, price, "img.jpg", "쇼핑몰", "https://link");
    }

    @Test
    @DisplayName("fetchAndUpsert: 기존 ingredient의 category는 보존된다 (핵심 invariant)")
    void existingCategoryIsPreservedOnUpsert() {
        // given: Naver에서 "오뚜기 밀가루 1kg"가 새 가격으로 들어옴
        NaverProduct refreshed = product("P-001", "오뚜기 밀가루 1kg", 3500);
        given(naverClient.search("밀가루")).willReturn(List.of(refreshed));

        // 기존 DB에 같은 productId, category="밀가루"로 등록된 row
        Ingredient existing = Ingredient.builder()
                .naverProductId("P-001")
                .title("오뚜기 밀가루 1kg (이전 제목)")
                .category("밀가루")  // ← 관리자가 직접 부여한 카테고리
                .price(3000)         // ← 이전 가격
                .totalAmount(new java.math.BigDecimal("1000"))
                .unit(Unit.G)
                .image("old.jpg")
                .mallName("이전몰")
                .link("https://old")
                .fetchedAt(java.time.LocalDateTime.now().minusDays(2))
                .build();
        given(ingredientRepository.findByNaverProductId("P-001")).willReturn(Optional.of(existing));

        // when
        int processed = ingredientService.fetchAndUpsert("밀가루");

        // then: 가격은 새값으로, category는 기존값 유지
        assertThat(processed).isEqualTo(1);
        assertThat(existing.getCategory()).isEqualTo("밀가루");      // ← 핵심
        assertThat(existing.getPrice()).isEqualTo(3500);               // ← 갱신됨
        assertThat(existing.getTitle()).isEqualTo("오뚜기 밀가루 1kg"); // ← 갱신됨

        // 기존 row 갱신 경로이므로 save는 호출되지 않음 (영속 상태에서 dirty checking)
        verify(ingredientRepository, never()).save(any(Ingredient.class));
    }

    @Test
    @DisplayName("fetchAndUpsert: 신규 product는 category=null로 insert")
    void newProductInsertedWithNullCategory() {
        // given: Naver 응답, DB에는 없는 신규 productId
        NaverProduct fresh = product("P-NEW", "신상품 설탕 500g", 2000);
        given(naverClient.search("설탕")).willReturn(List.of(fresh));
        given(ingredientRepository.findByNaverProductId("P-NEW")).willReturn(Optional.empty());

        // when
        int processed = ingredientService.fetchAndUpsert("설탕");

        // then: save 호출됨, category는 null
        assertThat(processed).isEqualTo(1);
        ArgumentCaptor<Ingredient> captor = ArgumentCaptor.forClass(Ingredient.class);
        verify(ingredientRepository, times(1)).save(captor.capture());
        Ingredient saved = captor.getValue();
        assertThat(saved.getCategory()).isNull();
        assertThat(saved.getNaverProductId()).isEqualTo("P-NEW");
        assertThat(saved.getTitle()).isEqualTo("신상품 설탕 500g");
        assertThat(saved.getPrice()).isEqualTo(2000);
    }

    @Test
    @DisplayName("fetchAndUpsert: 단위 파싱 실패한 product는 skip (저장 안 됨)")
    void unparsableProductIsSkipped() {
        // given: "특가" 같이 단위 없는 title → UnitParser 실패
        NaverProduct unparsable = product("P-BAD", "특가 행사 상품", 1000);
        NaverProduct ok = product("P-OK", "정상 밀가루 1kg", 3000);
        given(naverClient.search("밀가루")).willReturn(List.of(unparsable, ok));
        given(ingredientRepository.findByNaverProductId("P-OK")).willReturn(Optional.empty());

        // when
        int processed = ingredientService.fetchAndUpsert("밀가루");

        // then: 1개만 처리됨, P-BAD는 findBy / save 호출 X
        assertThat(processed).isEqualTo(1);
        verify(ingredientRepository, never()).findByNaverProductId("P-BAD");
        verify(ingredientRepository, times(1)).findByNaverProductId("P-OK");
        verify(ingredientRepository, times(1)).save(any(Ingredient.class));
    }

    @Test
    @DisplayName("fetchAndUpsert: 빈/blank 키워드는 즉시 0 반환 (Naver 호출 X)")
    void blankKeywordReturnsZero() {
        assertThat(ingredientService.fetchAndUpsert(null)).isZero();
        assertThat(ingredientService.fetchAndUpsert("")).isZero();
        assertThat(ingredientService.fetchAndUpsert("   ")).isZero();
        verify(naverClient, never()).search(any());
    }

    @Test
    @DisplayName("fetchAndUpsert: 기존+신규가 섞이면 각각 update / insert 처리")
    void mixedExistingAndNewAreHandledCorrectly() {
        NaverProduct p1 = product("P-OLD", "오뚜기 밀가루 1kg", 3500);
        NaverProduct p2 = product("P-NEW", "신상 밀가루 500g", 1800);
        given(naverClient.search("밀가루")).willReturn(List.of(p1, p2));

        Ingredient existing = Ingredient.builder()
                .naverProductId("P-OLD")
                .title("오뚜기 밀가루 1kg")
                .category("밀가루")
                .price(3000)
                .totalAmount(new java.math.BigDecimal("1000"))
                .unit(Unit.G)
                .image("old.jpg")
                .mallName("이전몰")
                .link("https://old")
                .fetchedAt(java.time.LocalDateTime.now().minusDays(2))
                .build();
        given(ingredientRepository.findByNaverProductId("P-OLD")).willReturn(Optional.of(existing));
        given(ingredientRepository.findByNaverProductId("P-NEW")).willReturn(Optional.empty());

        int processed = ingredientService.fetchAndUpsert("밀가루");

        assertThat(processed).isEqualTo(2);
        assertThat(existing.getCategory()).isEqualTo("밀가루");          // 보존
        assertThat(existing.getPrice()).isEqualTo(3500);                  // 갱신
        verify(ingredientRepository, times(1)).save(any(Ingredient.class)); // P-NEW만 insert
    }

    // ---- T3-18: updateCategory에서 카테고리 마스터 자동 등록 ----

    @Test
    @DisplayName("updateCategory: 새 카테고리 입력 시 categoryService.ensureExists 호출")
    void updateCategoryEnsuresMaster() {
        Ingredient ing = Ingredient.builder()
                .naverProductId("P-1")
                .title("제품")
                .category(null)
                .price(1000)
                .totalAmount(new java.math.BigDecimal("100"))
                .unit(Unit.G)
                .fetchedAt(java.time.LocalDateTime.now())
                .build();
        given(ingredientRepository.findById(1L)).willReturn(Optional.of(ing));

        ingredientService.updateCategory(1L, "박력분");

        assertThat(ing.getCategory()).isEqualTo("박력분");
        verify(categoryService, times(1)).ensureExists("박력분");
    }

    @Test
    @DisplayName("updateCategory: null/blank 카테고리 → ensureExists 호출 X")
    void updateCategoryWithNullSkipsMaster() {
        Ingredient ing = Ingredient.builder()
                .naverProductId("P-1")
                .title("제품")
                .category("기존")
                .price(1000)
                .totalAmount(new java.math.BigDecimal("100"))
                .unit(Unit.G)
                .fetchedAt(java.time.LocalDateTime.now())
                .build();
        given(ingredientRepository.findById(1L)).willReturn(Optional.of(ing));

        ingredientService.updateCategory(1L, null);

        assertThat(ing.getCategory()).isNull();
        verify(categoryService, never()).ensureExists(any());
    }

    // ---- T2-8: viewByCategory 비동기 트리거 정책 ----

    @Test
    @DisplayName("viewByCategory: stale 캐시 — 비동기 trigger만 호출, fetchAndUpsert 블로킹 X")
    void viewByCategoryWithStaleCacheTriggersAsync() {
        // given: 캐시에 fetchedAt 25시간 전 row 하나
        given(naverApiProperties.ttlHours()).willReturn(24);
        Ingredient stale = Ingredient.builder()
                .naverProductId("P-1")
                .title("오래된 밀가루")
                .category("밀가루")
                .price(3000)
                .totalAmount(new java.math.BigDecimal("1000"))
                .unit(Unit.G)
                .fetchedAt(java.time.LocalDateTime.now().minusHours(25))
                .build();
        given(ingredientRepository.findByCategory("밀가루")).willReturn(List.of(stale));

        // when
        List<Ingredient> result = ingredientService.viewByCategory("밀가루");

        // then: 즉시 캐시 반환 + 비동기 trigger 호출, 블로킹 fetch X
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isSameAs(stale);
        verify(refetchService, times(1)).triggerAsyncRefetch("밀가루");
        verify(naverClient, never()).search(any());          // 블로킹 fetch 없음
        verify(ingredientRepository, times(1)).findByCategory("밀가루"); // 재조회 안 함
    }

    @Test
    @DisplayName("viewByCategory: fresh 캐시 — trigger 호출 X")
    void viewByCategoryWithFreshCacheDoesNotTrigger() {
        given(naverApiProperties.ttlHours()).willReturn(24);
        Ingredient fresh = Ingredient.builder()
                .naverProductId("P-2")
                .title("신선한 밀가루")
                .category("밀가루")
                .price(3000)
                .totalAmount(new java.math.BigDecimal("1000"))
                .unit(Unit.G)
                .fetchedAt(java.time.LocalDateTime.now().minusHours(1)) // TTL 안쪽
                .build();
        given(ingredientRepository.findByCategory("밀가루")).willReturn(List.of(fresh));

        List<Ingredient> result = ingredientService.viewByCategory("밀가루");

        assertThat(result).hasSize(1);
        verify(refetchService, never()).triggerAsyncRefetch(any());
        verify(naverClient, never()).search(any());
    }

    @Test
    @DisplayName("viewByCategory: 빈 캐시 — 빈 리스트 즉시 반환 + 비동기 trigger")
    void viewByCategoryWithEmptyCacheTriggersAsync() {
        given(ingredientRepository.findByCategory("새카테")).willReturn(List.of());

        List<Ingredient> result = ingredientService.viewByCategory("새카테");

        assertThat(result).isEmpty();
        verify(refetchService, times(1)).triggerAsyncRefetch("새카테");
        verify(naverClient, never()).search(any());
    }

    @Test
    @DisplayName("viewByCategory: null/blank 카테고리 → 즉시 빈 리스트, 어떤 호출도 안 함")
    void viewByCategoryWithBlankReturnsEmpty() {
        assertThat(ingredientService.viewByCategory(null)).isEmpty();
        assertThat(ingredientService.viewByCategory("")).isEmpty();
        assertThat(ingredientService.viewByCategory("   ")).isEmpty();
        verify(ingredientRepository, never()).findByCategory(any());
        verify(refetchService, never()).triggerAsyncRefetch(any());
    }
}
