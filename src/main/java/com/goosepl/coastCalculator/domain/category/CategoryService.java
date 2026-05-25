package com.goosepl.coastCalculator.domain.category;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * T3-18: 카테고리 마스터 CRUD.
 *
 * - {@link #findAllNames()}: 폼 datalist용 가나다순 이름 목록
 * - {@link #ensureExists(String)}: admin이 ingredient에 새 카테고리를 부여하는 시점에
 *   마스터에 멱등 등록 (이미 있으면 skip). 빈 문자열/null은 무시.
 *
 * 카테고리 삭제는 의도적으로 노출하지 않음 — Ingredient.category가 String이라 마스터를 삭제해도
 * ingredient 데이터는 남고, 마스터에서만 빠지면 datalist에서 사라져 사용자 혼란만 줌.
 * 정리가 필요하면 별도 admin UI(미구현)에서.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<String> findAllNames() {
        return categoryRepository.findAllByOrderByNameAsc().stream()
                .map(Category::getName)
                .toList();
    }

    /**
     * 카테고리 마스터에 멱등 등록. null/blank는 no-op.
     * 동시성 시나리오에서 race condition 시 unique 제약이 보호 — 두 트랜잭션이 동시에
     * INSERT 시도해도 한쪽만 성공하고 다른쪽은 DataIntegrityViolation. 호출자(IngredientService.updateCategory)는
     * 별도 트랜잭션 내라 자체 재시도/무시는 일단 안 함 (드물고 admin만 호출).
     */
    @Transactional
    public void ensureExists(String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        String normalized = name.trim();
        Optional<Category> existing = categoryRepository.findByName(normalized);
        if (existing.isPresent()) {
            return;
        }
        categoryRepository.save(Category.builder().name(normalized).build());
        log.info("카테고리 마스터에 신규 등록: name={}", normalized);
    }
}
