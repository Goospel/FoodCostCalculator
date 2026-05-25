package com.goosepl.coastCalculator.domain.ingredient;

import com.goosepl.coastCalculator.config.NaverApiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * T2-8: IngredientRefetchService 단위 테스트.
 *
 * 검증 대상:
 *  - triggerAsyncRefetch: 카테고리별 락(중복 호출 skip), blank/null no-op, fetchAndUpsert 위임
 *  - scheduledRefresh: enabled=false면 repo 호출 X, stale 카테고리 each에 trigger 호출
 *
 * 비동기({@code @Async}) 자체는 단위 테스트에서 동기적으로 호출됨(프록시 없음). 락 동작과 위임만 검증.
 */
@ExtendWith(MockitoExtension.class)
class IngredientRefetchServiceTest {

    @Mock
    private IngredientRepository ingredientRepository;

    @Mock
    private NaverApiProperties naverProperties;

    @Mock
    private IngredientService ingredientService;

    @InjectMocks
    private IngredientRefetchService refetchService;

    @BeforeEach
    void enableScheduler() {
        // @Value 주입은 단위 테스트에서 안 됨 → 리플렉션으로 활성화
        ReflectionTestUtils.setField(refetchService, "scheduledRefreshEnabled", true);
    }

    @Test
    @DisplayName("triggerAsyncRefetch: 정상 호출 → fetchAndUpsert 위임 + 호출 후 락 해제")
    void triggersFetchAndUpsertAndReleasesLock() {
        given(ingredientService.fetchAndUpsert("밀가루")).willReturn(5);

        refetchService.triggerAsyncRefetch("밀가루");

        verify(ingredientService, times(1)).fetchAndUpsert("밀가루");
        // 락 해제 후 다시 호출 가능
        refetchService.triggerAsyncRefetch("밀가루");
        verify(ingredientService, times(2)).fetchAndUpsert("밀가루");
    }

    @Test
    @DisplayName("triggerAsyncRefetch: blank/null → no-op (fetchAndUpsert 호출 X)")
    void noOpForBlank() {
        refetchService.triggerAsyncRefetch(null);
        refetchService.triggerAsyncRefetch("");
        refetchService.triggerAsyncRefetch("   ");

        verify(ingredientService, never()).fetchAndUpsert(any());
    }

    @Test
    @DisplayName("triggerAsyncRefetch: 같은 카테고리 동시 실행 → 한 번만 fetch (락 동작)")
    void concurrentSameCategoryRunsOnce() throws InterruptedException {
        // 첫 번째 호출이 fetchAndUpsert 안에서 멈춰 있는 동안(latch), 두 번째 호출이 들어옴 → skip
        CountDownLatch holdInFetch = new CountDownLatch(1);
        CountDownLatch firstStarted = new CountDownLatch(1);
        AtomicInteger fetchCount = new AtomicInteger(0);

        given(ingredientService.fetchAndUpsert("밀가루")).willAnswer(inv -> {
            fetchCount.incrementAndGet();
            firstStarted.countDown();
            holdInFetch.await(2, TimeUnit.SECONDS); // 락 유지 중
            return 0;
        });

        Thread first = new Thread(() -> refetchService.triggerAsyncRefetch("밀가루"));
        first.start();

        assertThat(firstStarted.await(1, TimeUnit.SECONDS)).isTrue();
        // 첫 번째가 락 잡고 있는 동안 두 번째 호출 — skip 되어야 함
        refetchService.triggerAsyncRefetch("밀가루");

        holdInFetch.countDown();
        first.join(2000);

        assertThat(fetchCount.get()).isEqualTo(1); // 한 번만 호출됨
    }

    @Test
    @DisplayName("triggerAsyncRefetch: 다른 카테고리는 독립적인 락 — 동시 실행 가능")
    void differentCategoriesRunIndependently() {
        given(ingredientService.fetchAndUpsert(any())).willReturn(1);

        refetchService.triggerAsyncRefetch("밀가루");
        refetchService.triggerAsyncRefetch("설탕");

        verify(ingredientService).fetchAndUpsert("밀가루");
        verify(ingredientService).fetchAndUpsert("설탕");
    }

    @Test
    @DisplayName("triggerAsyncRefetch: fetchAndUpsert 실패 시에도 락 해제 (다음 호출 가능)")
    void releasesLockOnException() {
        given(ingredientService.fetchAndUpsert("밀가루"))
                .willThrow(new RuntimeException("boom"))
                .willReturn(1);

        refetchService.triggerAsyncRefetch("밀가루"); // 첫 호출은 예외 발생, 락 해제됨
        refetchService.triggerAsyncRefetch("밀가루"); // 두 번째 정상 — 락이 해제됐으니 통과

        verify(ingredientService, times(2)).fetchAndUpsert("밀가루");
    }

    @Test
    @DisplayName("scheduledRefresh: enabled=false → 즉시 return, repo 호출 X")
    void scheduledRefreshSkippedWhenDisabled() {
        ReflectionTestUtils.setField(refetchService, "scheduledRefreshEnabled", false);

        refetchService.scheduledRefresh();

        verify(ingredientRepository, never()).findDistinctStaleCategoriesBefore(any());
        verify(ingredientService, never()).fetchAndUpsert(any());
    }

    @Test
    @DisplayName("scheduledRefresh: stale 카테고리 each에 대해 trigger 호출")
    void scheduledRefreshTriggersAllStaleCategories() {
        given(naverProperties.ttlHours()).willReturn(24);
        given(ingredientRepository.findDistinctStaleCategoriesBefore(any()))
                .willReturn(List.of("밀가루", "설탕", "간장"));
        given(ingredientService.fetchAndUpsert(any())).willReturn(1);

        refetchService.scheduledRefresh();

        verify(ingredientService).fetchAndUpsert("밀가루");
        verify(ingredientService).fetchAndUpsert("설탕");
        verify(ingredientService).fetchAndUpsert("간장");
    }

    @Test
    @DisplayName("scheduledRefresh: stale 카테고리 없으면 trigger 호출 X")
    void scheduledRefreshDoesNothingWhenNoStale() {
        given(naverProperties.ttlHours()).willReturn(24);
        given(ingredientRepository.findDistinctStaleCategoriesBefore(any()))
                .willReturn(List.of());

        refetchService.scheduledRefresh();

        verify(ingredientService, never()).fetchAndUpsert(any());
    }
}
