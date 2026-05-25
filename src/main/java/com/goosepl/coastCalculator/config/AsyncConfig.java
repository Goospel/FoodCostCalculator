package com.goosepl.coastCalculator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * T2-8: 비동기 + 스케줄러 활성화.
 *
 * - {@link EnableAsync}: {@code @Async} 메서드를 별도 스레드 풀에서 실행
 * - {@link EnableScheduling}: {@code @Scheduled} 잡 활성화 (Spring 기본 단일 스레드 스케줄러 사용)
 *
 * Naver refetch 전용 executor를 빈 이름 {@code "naverRefetchExecutor"}로 노출 — 외부 호출/지수 backoff가
 * 톰캣 요청 스레드를 가로채지 않게 분리. core 2 / max 4 / queue 100 — Naver 호출 빈도(분당 수 회 수준)와
 * 단일 인스턴스 운영을 가정한 보수적 값.
 *
 * @Async 같은 클래스 self-call은 프록시 우회로 동작 안 함 → IngredientRefetchService를 IngredientService와
 * 분리해 주입(상호 호출은 IngredientService → RefetchService → IngredientService.fetchAndUpsert 단방향).
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    public static final String NAVER_REFETCH_EXECUTOR = "naverRefetchExecutor";

    @Bean(NAVER_REFETCH_EXECUTOR)
    public TaskExecutor naverRefetchExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("naver-refetch-");
        // 큐가 차면 호출자(스케줄러/사용자 트리거)가 즉시 빠지도록 — 외부 호출에 사용자 응답 묶이지 않게
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.DiscardPolicy());
        executor.initialize();
        return executor;
    }
}
