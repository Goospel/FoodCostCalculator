package com.goosepl.coastCalculator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 제휴 마케팅 설정. tracking-id가 비어 있으면 어필리에이트 파라미터 없이 평범한
 * 검색 URL만 노출 (수수료 X). 가입 후 발급받은 ID를 env로 주입하면 자동 활성화.
 */
@ConfigurationProperties(prefix = "affiliate")
public record AffiliateProperties(Coupang coupang) {

    public AffiliateProperties {
        if (coupang == null) {
            coupang = new Coupang(null, false);
        }
    }

    public record Coupang(String trackingId, Boolean enabled) {
        public Coupang {
            if (enabled == null) {
                enabled = trackingId != null && !trackingId.isBlank();
            }
        }

        public boolean isActive() {
            return Boolean.TRUE.equals(enabled) && trackingId != null && !trackingId.isBlank();
        }
    }
}
