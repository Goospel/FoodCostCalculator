package com.goosepl.coastCalculator.external.naver;

import com.goosepl.coastCalculator.external.naver.dto.NaverProduct;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T2-9 검증: {@link RealNaverShoppingClient}의 retry / recover 동작을 stub HTTP 서버로 통합 검증.
 *
 * <p>JDK 내장 {@link HttpServer}로 가짜 Naver 응답을 만들고, base-url을 그 stub으로 향하게 한 뒤
 * 응답 시나리오를 바꿔가며 retry 횟수 / 최종 fallback을 검증한다.
 *
 * <p>왜 통합 테스트인가:
 *  - {@link org.springframework.retry.annotation.Retryable}는 Spring AOP 프록시로 동작 — 컨테이너 안에서만 활성
 *  - 단위 테스트로는 어노테이션 존재만 확인할 수 있어 의미가 약함
 */
@SpringBootTest(properties = {
        "naver.api.mock-enabled=false",
        "naver.api.client-id=test-id",
        "naver.api.client-secret=test-secret",
        "naver.api.max-attempts=3",
        "naver.api.initial-backoff-ms=10",     // 테스트 빠르게: 10ms → 20ms → 40ms
        "naver.api.connect-timeout-ms=1000",
        "naver.api.read-timeout-ms=2000",
        "naver.api.ttl-hours=24",
        "naver.api.display=10"
})
@ActiveProfiles("test")
class RealNaverShoppingClientRetryTest {

    private static HttpServer stubServer;
    private static int stubPort;
    private static final AtomicInteger requestCount = new AtomicInteger();
    /** 0 = always 500, 1 = first N=500 then success, 2 = always success */
    private static volatile int mode = 0;
    private static volatile int failuresBeforeSuccess = 0;

    @Autowired
    private RealNaverShoppingClient client;

    @DynamicPropertySource
    static void registerBaseUrl(DynamicPropertyRegistry registry) throws IOException {
        if (stubServer == null) {
            stubServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            stubPort = stubServer.getAddress().getPort();
            stubServer.createContext("/test/shop", RealNaverShoppingClientRetryTest::handle);
            stubServer.setExecutor(null);
            stubServer.start();
        }
        registry.add("naver.api.base-url", () -> "http://127.0.0.1:" + stubPort + "/test/shop");
    }

    @AfterAll
    static void stopServer() {
        if (stubServer != null) {
            stubServer.stop(0);
            stubServer = null;
        }
    }

    @BeforeAll
    static void initState() {
        requestCount.set(0);
    }

    @AfterEach
    void resetCounters() {
        requestCount.set(0);
        mode = 0;
        failuresBeforeSuccess = 0;
    }

    private static void handle(HttpExchange exchange) throws IOException {
        int n = requestCount.incrementAndGet();
        if (mode == 1 && n > failuresBeforeSuccess) {
            sendOk(exchange);
        } else if (mode == 2) {
            sendOk(exchange);
        } else {
            // 500 error
            exchange.sendResponseHeaders(500, -1);
        }
        exchange.close();
    }

    private static void sendOk(HttpExchange exchange) throws IOException {
        String body = """
                {"total":1,"start":1,"display":10,"items":[{"title":"테스트 밀가루 1kg","link":"https://example","image":"img.jpg","lprice":"3000","hprice":"","mallName":"쇼핑몰","productId":"P-1","productType":"1","brand":"","maker":""}]}""";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    @Test
    @DisplayName("5xx 2회 → 3번째 성공: 정확히 3번 호출, 결과 정상 반환")
    void retriesUntilSuccess() {
        mode = 1;
        failuresBeforeSuccess = 2;

        List<NaverProduct> products = client.search("밀가루");

        // 우선 호출 횟수가 retry로 누적됐는지 확인 (실패 시 1회만 호출되면 retry 미동작)
        assertThat(requestCount.get())
                .as("retry가 동작했다면 3회 호출되어야 함 (실패 2 + 성공 1)")
                .isEqualTo(3);
        assertThat(products).hasSize(1);
        assertThat(products.get(0).naverProductId()).isEqualTo("P-1");
    }

    @Test
    @DisplayName("5xx 영속 실패: maxAttempts 만큼 호출 후 @Recover로 빈 리스트 반환")
    void recoverWithEmptyListWhenAllRetriesFail() {
        mode = 0;   // always 500

        List<NaverProduct> products = client.search("밀가루");

        assertThat(requestCount.get()).isEqualTo(3);   // maxAttempts=3
        assertThat(products).isEmpty();                  // recover fallback
    }

    @Test
    @DisplayName("blank/null 키워드는 즉시 빈 리스트 — Naver 호출 X")
    void blankKeywordSkipsNetworkCall() {
        assertThat(client.search(null)).isEmpty();
        assertThat(client.search("")).isEmpty();
        assertThat(client.search("   ")).isEmpty();

        assertThat(requestCount.get()).isZero();
    }
}
