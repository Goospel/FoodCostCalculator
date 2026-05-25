package com.goosepl.coastCalculator.domain.ingredient;

import com.goosepl.coastCalculator.config.NaverApiProperties;
import com.goosepl.coastCalculator.domain.category.CategoryService;
import com.goosepl.coastCalculator.external.naver.NaverShoppingClient;
import com.goosepl.coastCalculator.external.naver.dto.NaverProduct;
import com.goosepl.coastCalculator.external.naver.parser.UnitParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class IngredientService {

    private final IngredientRepository ingredientRepository;
    private final NaverShoppingClient naverClient;
    private final NaverApiProperties properties;
    private final CategoryService categoryService;
    /**
     * T2-8: 비동기 refetch 트리거. {@link Lazy}는 IngredientService ↔ IngredientRefetchService
     * 양방향 의존(refetch가 다시 fetchAndUpsert 호출)의 부팅 초기화 순서 보호.
     */
    private final IngredientRefetchService refetchService;
    /** T3-19: 가격 이력 적재 — fetchAndUpsert에서 변동 감지 시. */
    private final IngredientPriceHistoryRepository priceHistoryRepository;

    @Autowired
    public IngredientService(IngredientRepository ingredientRepository,
                             NaverShoppingClient naverClient,
                             NaverApiProperties properties,
                             CategoryService categoryService,
                             @Lazy IngredientRefetchService refetchService,
                             IngredientPriceHistoryRepository priceHistoryRepository) {
        this.ingredientRepository = ingredientRepository;
        this.naverClient = naverClient;
        this.properties = properties;
        this.categoryService = categoryService;
        this.refetchService = refetchService;
        this.priceHistoryRepository = priceHistoryRepository;
    }

    @Transactional
    public int fetchAndUpsert(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return 0;
        }
        List<NaverProduct> products = naverClient.search(keyword);
        int processed = 0;
        for (NaverProduct product : products) {
            Optional<UnitParser.ParsedAmount> parsed = UnitParser.parse(product.title());
            if (parsed.isEmpty()) {
                log.debug("단위 파싱 실패로 skip: title={}", product.title());
                continue;
            }
            UnitParser.ParsedAmount amount = parsed.get();
            LocalDateTime now = LocalDateTime.now();

            Optional<Ingredient> existing = ingredientRepository.findByNaverProductId(product.naverProductId());
            if (existing.isPresent()) {
                Ingredient ing = existing.get();
                // T3-19: 가격 변동 감지를 위해 갱신 직전 pricePerGram 백업
                java.math.BigDecimal previousPpg = ing.getPricePerGram();
                // 카테고리는 절대 건드리지 않음
                ing.refreshFromNaver(
                        product.title(),
                        product.price(),
                        amount.amount(),
                        amount.unit(),
                        product.image(),
                        product.mallName(),
                        product.link(),
                        now
                );
                // T3-19: pricePerGram 변동 시만 history 적재 (BigDecimal.compareTo로 scale 무시 비교)
                if (previousPpg == null || previousPpg.compareTo(ing.getPricePerGram()) != 0) {
                    priceHistoryRepository.save(IngredientPriceHistory.snapshotOf(ing, now));
                }
            } else {
                Ingredient ingredient = Ingredient.builder()
                        .naverProductId(product.naverProductId())
                        .title(product.title())
                        .category(null) // 관리자가 채울 영역
                        .price(product.price())
                        .totalAmount(amount.amount())
                        .unit(amount.unit())
                        .image(product.image())
                        .mallName(product.mallName())
                        .link(product.link())
                        .fetchedAt(now)
                        .build();
                ingredientRepository.save(ingredient);
                // T3-19: 신규 ingredient는 첫 history row (시작 시점) 적재.
                // save 반환값 대신 같은 영속 인스턴스 사용 — JPA 동일 트랜잭션 내라 안전.
                priceHistoryRepository.save(IngredientPriceHistory.snapshotOf(ingredient, now));
            }
            processed++;
        }
        log.info("Naver fetchAndUpsert 완료: keyword={}, 응답수={}, upsert={}",
                keyword, products.size(), processed);
        return processed;
    }

    /**
     * T2-8: 사용자 조회는 Naver 호출에 절대 블로킹 X.
     *
     * - 항상 캐시(stale 허용) 즉시 반환
     * - stale 또는 빈 결과 감지 시 {@link IngredientRefetchService#triggerAsyncRefetch}로 백그라운드만 트리거
     * - 다음 사용자 진입 또는 스케줄러 사이클에 신선한 결과 반영
     *
     * 이전 동작(블로킹 fetchAndUpsert)은 Naver 장애 시 톰캣 스레드 고갈 위험 — T2-8로 제거.
     */
    @Transactional(readOnly = true)
    public List<Ingredient> viewByCategory(String category) {
        if (category == null || category.isBlank()) {
            return List.of();
        }
        List<Ingredient> rows = ingredientRepository.findByCategory(category);
        if (isStale(rows)) {
            log.info("TTL 만료/빈 결과 → 비동기 refetch 트리거(블로킹 X): category={}", category);
            refetchService.triggerAsyncRefetch(category);
        }
        return rows;
    }

    private boolean isStale(List<Ingredient> rows) {
        if (rows.isEmpty()) {
            return true;
        }
        LocalDateTime threshold = LocalDateTime.now().minusHours(properties.ttlHours());
        return rows.stream().anyMatch(r -> r.getFetchedAt().isBefore(threshold));
    }

    @Transactional(readOnly = true)
    public List<Ingredient> findAllVisible() {
        return ingredientRepository.findByCategoryIsNotNullOrderByCategoryAscPricePerGramAsc();
    }

    @Transactional(readOnly = true)
    public List<Ingredient> findAllForAdmin() {
        return ingredientRepository.findAllByOrderByCategoryAscPricePerGramAsc();
    }

    @Transactional(readOnly = true)
    public Ingredient findById(Long id) {
        return ingredientRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("재료를 찾을 수 없습니다: id=" + id));
    }

    @Transactional
    public void updateCategory(Long id, String category) {
        Ingredient ingredient = ingredientRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("재료를 찾을 수 없습니다: id=" + id));
        String normalized = (category == null || category.isBlank()) ? null : category.trim();
        ingredient.updateCategory(normalized);
        // T3-18: admin이 새 카테고리를 부여하면 마스터에 멱등 등록 → datalist 자동완성에 노출
        if (normalized != null) {
            categoryService.ensureExists(normalized);
        }
    }

    @Transactional
    public void delete(Long id) {
        if (!ingredientRepository.existsById(id)) {
            throw new IllegalArgumentException("재료를 찾을 수 없습니다: id=" + id);
        }
        ingredientRepository.deleteById(id);
    }
}
