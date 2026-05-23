package com.goosepl.coastCalculator.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

/**
 * Spring Retry 활성화.
 *
 * 적용 대상:
 *  - {@link com.goosepl.coastCalculator.external.naver.RealNaverShoppingClient#search(String)}
 *    — Naver API 호출 시 일시적 네트워크/5xx 오류에 대해 지수 backoff 재시도
 *
 * 정책 값(`naver.api.max-attempts`, `initial-backoff-ms`)은 {@link NaverApiProperties} 참조.
 */
@Configuration
@EnableRetry
public class RetryConfig {
}
