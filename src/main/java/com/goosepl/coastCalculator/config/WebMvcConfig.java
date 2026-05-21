package com.goosepl.coastCalculator.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * /uploads/** 경로를 로컬 파일시스템의 {@code storage.upload-dir}로 매핑한다.
 * SecurityConfig에서 {@code /uploads/**}는 permitAll로 열려 있음.
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final StorageProperties storageProperties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String absolute = Paths.get(storageProperties.uploadDir())
                .toAbsolutePath()
                .normalize()
                .toString()
                .replace('\\', '/');
        if (!absolute.endsWith("/")) {
            absolute = absolute + "/";
        }
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + absolute);
    }
}
