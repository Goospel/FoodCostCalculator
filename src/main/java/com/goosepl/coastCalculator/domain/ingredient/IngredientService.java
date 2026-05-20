package com.goosepl.coastCalculator.domain.ingredient;

import com.goosepl.coastCalculator.config.NaverApiProperties;
import com.goosepl.coastCalculator.external.naver.NaverShoppingClient;
import com.goosepl.coastCalculator.external.naver.dto.NaverProduct;
import com.goosepl.coastCalculator.external.naver.parser.UnitParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class IngredientService {

    private final IngredientRepository ingredientRepository;
    private final NaverShoppingClient naverClient;
    private final NaverApiProperties properties;

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
                // 카테고리는 절대 건드리지 않음
                existing.get().refreshFromNaver(
                        product.title(),
                        product.price(),
                        amount.amount(),
                        amount.unit(),
                        product.image(),
                        product.mallName(),
                        product.link(),
                        now
                );
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
            }
            processed++;
        }
        log.info("Naver fetchAndUpsert 완료: keyword={}, 응답수={}, upsert={}",
                keyword, products.size(), processed);
        return processed;
    }

    @Transactional
    public List<Ingredient> viewByCategory(String category) {
        if (category == null || category.isBlank()) {
            return List.of();
        }
        List<Ingredient> rows = ingredientRepository.findByCategory(category);
        if (isStale(rows)) {
            log.info("TTL 만료 또는 빈 결과, refetch 트리거: category={}", category);
            fetchAndUpsert(category);
            rows = ingredientRepository.findByCategory(category);
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
    }

    @Transactional
    public void delete(Long id) {
        if (!ingredientRepository.existsById(id)) {
            throw new IllegalArgumentException("재료를 찾을 수 없습니다: id=" + id);
        }
        ingredientRepository.deleteById(id);
    }
}
