package com.goosepl.coastCalculator.domain.category;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * T3-18.2: 카테고리 alias 풀이 + admin CRUD.
 *
 * 정책 (옵션 A): 매칭 단계에서만 정규화. {@link #resolve(String)}이 핵심 — input이
 * alias면 canonical 이름 반환, 아니면 input 그대로(공백 trim만).
 *
 * 검증 규칙 ({@link #add(String, String)}):
 *   1. alias는 null/blank 금지
 *   2. canonical 카테고리가 존재해야 함 (categories 테이블)
 *   3. alias는 categories.name 집합과 disjoint — 이미 canonical로 등록된 이름은 alias 못 됨
 *      (단순화: alias로 등록해도 resolve가 categories에서 먼저 찾으면 그건 canonical 취급이라 의미 모호)
 *   4. alias는 unique (DB 제약 + 사전 검사로 사용자 친화 메시지)
 *   5. alias == canonical name 금지 (자기 자신 가리킴 무의미)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryAliasService {

    private final CategoryAliasRepository aliasRepository;
    private final CategoryRepository categoryRepository;

    /**
     * input이 alias면 canonical 카테고리 이름 반환, 아니면 input 그대로.
     * null/blank input은 그대로 (정규화 없이 null 보존).
     *
     * 우선순위:
     *   1) input이 categories.name에 있으면 → input (이미 canonical, alias 풀이 X)
     *   2) input이 category_aliases.alias에 있으면 → canonical.name
     *   3) 둘 다 없으면 → input (사용자 자유 입력 보존)
     */
    /**
     * T2-10: 원가 계산 행마다 호출되는 최핫패스. Caffeine 캐시.
     * key는 input 그대로. condition으로 null/blank를 사전 차단 — Caffeine은 null key 거부(IllegalArgumentException).
     * 무효화: {@link #add}, {@link #delete} 시 allEntries (특정 key만 evict하려면 input 표준화가 선행되어야 함).
     */
    @Cacheable(cacheNames = "categoryAliasMap", key = "#input",
            condition = "#input != null and !#input.isBlank()")
    @Transactional(readOnly = true)
    public String resolve(String input) {
        if (input == null) {
            return null;
        }
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        // 1) canonical이면 그대로
        if (categoryRepository.existsByName(trimmed)) {
            return trimmed;
        }
        // 2) alias면 풀어서
        Optional<CategoryAlias> alias = aliasRepository.findByAlias(trimmed);
        if (alias.isPresent()) {
            return alias.get().getCanonical().getName();
        }
        // 3) 모르는 이름이면 그대로
        return trimmed;
    }

    @Transactional(readOnly = true)
    public List<CategoryAlias> findAll() {
        return aliasRepository.findAllByOrderByAliasAsc();
    }

    /**
     * alias 등록. 위 5가지 검증 통과 후 저장.
     * T2-10: alias 추가는 resolve 결과를 바꾸므로 categoryAliasMap 전체 무효화.
     * categoryNames도 무효화 — resolve가 categories.name 우선이라 의존성 있음(보수적).
     */
    @Caching(evict = {
            @CacheEvict(cacheNames = "categoryAliasMap", allEntries = true),
            @CacheEvict(cacheNames = "categoryNames", allEntries = true)
    })
    @Transactional
    public CategoryAlias add(String aliasName, String canonicalName) {
        if (aliasName == null || aliasName.isBlank()) {
            throw new IllegalArgumentException("alias 이름을 입력해주세요");
        }
        if (canonicalName == null || canonicalName.isBlank()) {
            throw new IllegalArgumentException("canonical 카테고리를 선택해주세요");
        }
        String trimmedAlias = aliasName.trim();
        String trimmedCanonical = canonicalName.trim();

        if (trimmedAlias.equals(trimmedCanonical)) {
            throw new IllegalArgumentException("alias와 canonical 카테고리 이름이 같을 수 없습니다: " + trimmedAlias);
        }
        if (categoryRepository.existsByName(trimmedAlias)) {
            throw new IllegalArgumentException(
                    "이미 canonical 카테고리로 등록된 이름은 alias로 쓸 수 없습니다: " + trimmedAlias);
        }
        if (aliasRepository.existsByAlias(trimmedAlias)) {
            throw new IllegalArgumentException("이미 등록된 alias입니다: " + trimmedAlias);
        }
        Category canonical = categoryRepository.findByName(trimmedCanonical)
                .orElseThrow(() -> new IllegalArgumentException(
                        "canonical 카테고리를 찾을 수 없습니다: " + trimmedCanonical));

        CategoryAlias saved = aliasRepository.save(
                CategoryAlias.builder().alias(trimmedAlias).canonical(canonical).build());
        log.info("카테고리 alias 등록: {} → {}", trimmedAlias, trimmedCanonical);
        return saved;
    }

    @CacheEvict(cacheNames = "categoryAliasMap", allEntries = true)
    @Transactional
    public void delete(Long id) {
        CategoryAlias alias = aliasRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("alias를 찾을 수 없습니다: id=" + id));
        aliasRepository.delete(alias);
        log.info("카테고리 alias 삭제: id={}, alias={}", id, alias.getAlias());
    }
}
