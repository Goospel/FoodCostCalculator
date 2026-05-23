package com.goosepl.coastCalculator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Naver Search API 설정. T2-9에서 connect/read 타임아웃과 retry 정책 필드 추가.
 *
 * @param baseUrl          API 엔드포인트
 * @param clientId         X-Naver-Client-Id 헤더
 * @param clientSecret     X-Naver-Client-Secret 헤더
 * @param ttlHours         캐시 stale 판정 임계값 (시간 단위)
 * @param display          한 번에 가져올 결과 수 (네이버 최대 100)
 * @param mockEnabled      true면 MockNaverShoppingClient 활성
 * @param connectTimeoutMs HTTP connect 타임아웃 (ms). 기본 5초
 * @param readTimeoutMs    HTTP read 타임아웃 (ms). 기본 10초
 * @param maxAttempts      retry 총 시도 횟수 (1 = retry 없음). 기본 3 — 첫 시도 + 재시도 2회
 * @param initialBackoffMs retry 사이 첫 backoff (ms). 지수 2배 증가. 기본 1초 → 2초 → 4초
 */
@ConfigurationProperties(prefix = "naver.api")
public record NaverApiProperties(
        String baseUrl,
        String clientId,
        String clientSecret,
        int ttlHours,
        int display,
        boolean mockEnabled,
        int connectTimeoutMs,
        int readTimeoutMs,
        int maxAttempts,
        long initialBackoffMs
) {
    /** 기본값 보정 — 설정 누락 시 안전한 값으로. */
    public NaverApiProperties {
        if (connectTimeoutMs <= 0) connectTimeoutMs = 5_000;
        if (readTimeoutMs <= 0) readTimeoutMs = 10_000;
        if (maxAttempts <= 0) maxAttempts = 3;
        if (initialBackoffMs <= 0) initialBackoffMs = 1_000L;
    }
}
