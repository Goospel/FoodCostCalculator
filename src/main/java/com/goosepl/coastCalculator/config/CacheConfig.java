package com.goosepl.coastCalculator.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * T2-10: Caffeine in-memory 캐시 활성화.
 *
 * 적용 대상 (모두 읽기 핫패스, 변경 빈도 낮음):
 *  - {@link com.goosepl.coastCalculator.domain.category.CategoryService#findAllNames()} — datalist
 *  - {@link com.goosepl.coastCalculator.domain.category.CategoryAliasService#resolve(String)} — 원가 계산 루프
 *  - {@link com.goosepl.coastCalculator.domain.ingredient.IngredientService#findAllVisible()} — recipe 폼 그루핑
 *
 * 페이징 메서드(`findRecent`/`searchByName`)는 키 가변성 + write-through evict 비용으로 의도적 비포함.
 * `viewByCategory`는 T2-8의 DB 캐시(stale 허용) + async refetch로 충분.
 *
 * Spring Boot autoconfig + `application.yaml`의 `spring.cache.*` spec으로 충분 — 별도 CacheManager 빈 없음.
 * 테스트는 `application-test.yaml`에서 `spring.cache.type=none`으로 NoOp.
 *
 * Redis 전환은 멀티 인스턴스(≥ 2) 시점에 type만 바꾸면 됨 — `@Cacheable`/`@CacheEvict` 인터페이스 그대로.
 */
@Configuration
@EnableCaching
public class CacheConfig {
}
