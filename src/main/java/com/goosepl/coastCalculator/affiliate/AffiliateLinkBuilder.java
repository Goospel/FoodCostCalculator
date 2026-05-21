package com.goosepl.coastCalculator.affiliate;

import com.goosepl.coastCalculator.config.AffiliateProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 제휴 마케팅 링크 생성기. Thymeleaf 템플릿에서
 * {@code ${@affiliateLinkBuilder.coupangSearch(...)}} 로 호출.
 *
 * tracking-id가 설정되어 있으면 어필리에이트 파라미터(`lptag`)를 부착,
 * 없으면 일반 쿠팡 검색 URL만 반환 (수수료 X, 동작은 동일).
 */
@Component
@RequiredArgsConstructor
public class AffiliateLinkBuilder {

    private static final String COUPANG_SEARCH_BASE = "https://www.coupang.com/np/search";

    private final AffiliateProperties properties;

    /**
     * 쿠팡 검색 결과 URL을 생성한다.
     * 어필리에이트 활성 시 {@code &lptag=ID&traceid=...} 부착.
     */
    public String coupangSearch(String query) {
        if (query == null || query.isBlank()) {
            return COUPANG_SEARCH_BASE;
        }
        String encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);
        StringBuilder url = new StringBuilder(COUPANG_SEARCH_BASE)
                .append("?q=").append(encoded)
                .append("&channel=user");
        if (properties.coupang() != null && properties.coupang().isActive()) {
            url.append("&lptag=").append(URLEncoder.encode(properties.coupang().trackingId(), StandardCharsets.UTF_8));
        }
        return url.toString();
    }

    /**
     * 현재 어필리에이트 활성 상태. UI에서 "쿠팡에서 보기" 버튼 노출 여부 등에 활용.
     */
    public boolean isCoupangActive() {
        return properties.coupang() != null && properties.coupang().isActive();
    }
}
