package com.goosepl.coastCalculator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "naver.api")
public record NaverApiProperties(
        String baseUrl,
        String clientId,
        String clientSecret,
        int ttlHours,
        int display,
        boolean mockEnabled
) {
}
