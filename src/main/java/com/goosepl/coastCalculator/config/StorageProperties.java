package com.goosepl.coastCalculator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 파일 스토리지 설정. application.yaml의 storage.* 매핑.
 * 예: storage.upload-dir=./uploads
 */
@ConfigurationProperties(prefix = "storage")
public record StorageProperties(String uploadDir) {

    public StorageProperties {
        if (uploadDir == null || uploadDir.isBlank()) {
            uploadDir = "./uploads";
        }
    }
}
