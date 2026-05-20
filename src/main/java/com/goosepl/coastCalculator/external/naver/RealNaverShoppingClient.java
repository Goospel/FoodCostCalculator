package com.goosepl.coastCalculator.external.naver;

import com.goosepl.coastCalculator.config.NaverApiProperties;
import com.goosepl.coastCalculator.external.naver.dto.NaverProduct;
import com.goosepl.coastCalculator.external.naver.dto.NaverShopApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Component
@ConditionalOnProperty(name = "naver.api.mock-enabled", havingValue = "false")
public class RealNaverShoppingClient implements NaverShoppingClient {

    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");

    private final NaverApiProperties properties;
    private final RestClient restClient;

    public RealNaverShoppingClient(NaverApiProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.create();
    }

    @Override
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
