package com.goosepl.coastCalculator.domain.ingredient;

import com.goosepl.coastCalculator.config.AsyncConfig;
import com.goosepl.coastCalculator.config.NaverApiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * T2-8: 비동기/스케줄러 기반 Naver refetch.
 *
 * 정책:
 *   - 사용자 요청({@link IngredientService#viewByCategory})은 외부 호출을 절대 블로킹하지 않음
 *   - stale 감지되면 {@link #triggerAsyncRefetch}로 백그라운드 트리거만 — 결과는 다음 사용자 진입에 반영
 *   - 추가로 {@link #scheduledRefresh}가 주기적(기본 1시간)으로 stale 카테고리 일괄 갱신
 *
 * 중복 방지: 카테고리별 락({@link ConcurrentHashMap}+{@link AtomicBoolean})으로 같은 카테고리가
 * 동시에 두 번 refetch 되지 않게 함. 락 실패는 silently skip — 이미 진행 중이라는 의미.
 *
 * {@code @Async}는 같은 클래스 self-call 시 프록시 우회로 동작 안 함 → IngredientService에서
 * 이 빈을 주입해 호출해야 비동기로 실행됨. {@link Lazy}는 IngredientService ↔ RefetchService
 * 양방향 순환(viewByCategory → trigger, scheduledRefresh → fetchAndUpsert) 시 부팅 초기화 순서 보호.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IngredientRefetchService {

    private final IngredientRepository ingredientRepository;
    private final NaverApiProperties naverProperties;
    @Lazy
    private final IngredientService ingredientService;

    /** 스케줄러 활성 여부 (테스트 등에서 끄기 위함). 기본 true. */
    @Value("${naver.api.scheduled-refresh-enabled:true}")
    private boolean scheduledRefreshEnabled;

    /** 카테고리별 진행 중 여부 — 같은 카테고리 중복 refetch 방지. */
    private final ConcurrentHashMap<String, AtomicBoolean> inProgress = new ConcurrentHashMap<>();

    /**
     * 비동기로 단일 카테고리 refetch 트리거.
     * 이미 진행 중이거나 큐가 가득 차면(DiscardPolicy) 그냥 skip — 다음 트리거를 기다림.
     *
     * @param category 갱신할 카테고리(자유 입력 그대로 — alias 풀이는 호출자 책임)
     */
    @Async(AsyncConfig.NAVER_REFETCH_EXECUTOR)
    public void triggerAsyncRefetch(String category) {
        if (category == null || category.isBlank()) {
            return;
        }
        String key = category.trim();
        AtomicBoolean lock = inProgress.computeIfAbsent(key, k -> new AtomicBoolean(false));
        if (!lock.compareAndSet(false, true)) {
            log.debug("이미 진행 중인 refetch, skip: category={}", key);
            return;
        }
        try {
            log.info("비동기 refetch 시작: category={}", key);
            int upserted = ingredientService.fetchAndUpsert(key);
            log.info("비동기 refetch 완료: category={}, upserted={}", key, upserted);
        } catch (Exception e) {
            // @Recover fallback이 RealNaverShoppingClient에 있으니 여기까진 거의 안 옴.
            // 그래도 비동기 잡이 silently die 하지 않도록 명시 로그.
            log.warn("비동기 refetch 실패(스레드는 살아남음): category={}, error={}", key, e.getMessage());
        } finally {
            lock.set(false);
        }
    }

    /**
     * 주기 스케줄러 — 기본 1시간(테스트는 비활성 플래그로 OFF). stale row를 가진 카테고리 일괄 trigger.
     *
     * fixedDelayString = 이전 잡 종료 후 N ms 대기. 카테고리가 많아 한 사이클이 길어져도 중첩 X.
     * {@code @Scheduled}는 메서드에 {@code @ConditionalOnProperty}가 적용 안 되므로, 메서드 내부에서
     * {@code scheduledRefreshEnabled} 플래그 체크. 비활성이면 즉시 return (빈 잡 비용 무시 수준).
     */
    @Scheduled(
            fixedDelayString = "${naver.api.scheduled-refresh-interval-ms:3600000}",
            initialDelayString = "${naver.api.scheduled-refresh-initial-delay-ms:60000}"
    )
    public void scheduledRefresh() {
        if (!scheduledRefreshEnabled) {
            return;
        }
        LocalDateTime threshold = LocalDateTime.now().minusHours(naverProperties.ttlHours());
        List<String> staleCategories = ingredientRepository.findDistinctStaleCategoriesBefore(threshold);
        if (staleCategories.isEmpty()) {
            log.debug("스케줄 refetch: stale 카테고리 없음");
            return;
        }
        log.info("스케줄 refetch 시작: stale 카테고리 {}개 — {}", staleCategories.size(), staleCategories);
        for (String category : staleCategories) {
            triggerAsyncRefetch(category);
        }
    }
}
