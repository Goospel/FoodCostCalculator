package com.goosepl.coastCalculator.external.naver;

import com.goosepl.coastCalculator.config.NaverApiProperties;
import com.goosepl.coastCalculator.external.naver.dto.NaverProduct;
import com.goosepl.coastCalculator.external.naver.dto.NaverShopApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Naver Shopping API 실제 호출 클라이언트.
 *
 * T2-9에서 보강된 안정성:
 *  - {@link JdkClientHttpRequestFactory}로 connect/read 타임아웃 명시
 *  - {@link Retryable}로 일시적 장애(네트워크, 5xx)에 지수 backoff 재시도
 *  - {@link Recover}로 최종 실패 시 빈 리스트 + WARN — 사용자 UX 보호
 *
 * Retry 트리거 예외:
 *  - {@link ResourceAccessException} — 타임아웃, connection refused 등 (RestClient 가 IOException을 래핑)
 *  - {@link HttpServerErrorException} — 5xx 서버 오류
 *
 * Retry 미트리거(즉시 실패):
 *  - 4xx (잘못된 키, 잘못된 키워드 등) — 재시도해도 같은 결과
 *  - 그 외 {@link RuntimeException} — Recover에서 처리
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "naver.api.mock-enabled", havingValue = "false")
public class RealNaverShoppingClient implements NaverShoppingClient {

    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");

    private final NaverApiProperties properties;
    private final RestClient restClient;

    public RealNaverShoppingClient(NaverApiProperties properties) {
        this.properties = properties;
        this.restClient = buildRestClient(properties);
        log.info("[Naver] RealClient 초기화: connectTimeout={}ms, readTimeout={}ms, maxAttempts={}, initialBackoff={}ms",
                properties.connectTimeoutMs(), properties.readTimeoutMs(),
                properties.maxAttempts(), properties.initialBackoffMs());
    }

    private static RestClient buildRestClient(NaverApiProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.connectTimeoutMs()))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(properties.readTimeoutMs()));
        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    @Override
    @Retryable(
            // 일시적 장애만 재시도. 4xx (예: 401, 400)는 재시도 무의미해서 제외.
            retryFor = {ResourceAccessException.class, HttpServerErrorException.class},
            maxAttemptsExpression = "${naver.api.max-attempts:3}",
            backoff = @Backoff(
                    delayExpression = "${naver.api.initial-backoff-ms:1000}",
                    multiplier = 2.0
            )
    )
    public List<NaverProduct> search(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        URI uri = UriComponentsBuilder.fromUriString(properties.baseUrl())
                .queryParam("query", keyword)
                .queryParam("display", properties.display())
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUri();

        log.info("[Naver] 검색 요청: keyword={}, uri={}", keyword, uri);

        NaverShopApiResponse response = restClient.get()
                .uri(uri)
                .header("X-Naver-Client-Id", properties.clientId())
                .header("X-Naver-Client-Secret", properties.clientSecret())
                .retrieve()
                .body(NaverShopApiResponse.class);

        if (response == null || response.items() == null) {
            return List.of();
        }
        return response.items().stream()
                .map(RealNaverShoppingClient::toProduct)
                .toList();
    }

    /**
     * Retry 모두 실패한 경우의 fallback. 빈 리스트 + WARN 로그.
     * 사용자 UX는 캐시된 데이터로 보호되고, 운영자는 로그로 장애 인지.
     *
     * <p>주의: {@code @Recover} 메서드 시그니처는 retry 대상 메서드와 반환형이 같아야 하며,
     * 첫 인자는 던져진 예외 타입이어야 한다. 두 종류의 예외를 한 메서드로 받기 위해 상위 타입
     * {@link RestClientException}으로 받음.
     */
    @Recover
    public List<NaverProduct> recoverSearch(RestClientException ex, String keyword) {
        log.warn("[Naver] 재시도 모두 실패 — 빈 리스트로 fallback. keyword={}, cause={}, msg={}",
                keyword, ex.getClass().getSimpleName(), ex.getMessage());
        return List.of();
    }

    private static NaverProduct toProduct(NaverShopApiResponse.NaverShopApiItem item) {
        int price = parsePrice(item.lprice());
        return new NaverProduct(
                item.productId(),
                stripHtml(item.title()),
                price,
                item.image(),
                item.mallName(),
                item.link()
        );
    }

    private static int parsePrice(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String stripHtml(String s) {
        return s == null ? null : HTML_TAG.matcher(s).replaceAll("");
    }
}
