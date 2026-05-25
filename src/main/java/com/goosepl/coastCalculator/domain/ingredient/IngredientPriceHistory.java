package com.goosepl.coastCalculator.domain.ingredient;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * T3-19: 식재료 가격 시계열 이력.
 *
 * <p>정책 (사용자 옵션 B): 가격 변동(pricePerGram BigDecimal.compareTo != 0) 시에만 적재.
 * 신규 ingredient는 첫 fetch 시 무조건 적재 (시작 시점). 동일 가격 fetch는 적재 X.</p>
 *
 * <p>관계: {@code ingredient_id} FK CASCADE — ingredient 삭제 시 이력도 함께. naverProductId는
 * 감사/추적용으로 ingredient에서 복사해서 보관(엔티티 삭제 후에도 어떤 제품이었는지 확인 가능).</p>
 *
 * <p>인덱스 {@code (ingredient_id, recorded_at DESC)}로 최근 N개 조회 최적화.</p>
 *
 * <p>수익화 활용:
 * <ul>
 *   <li>단계 3 Freemium 차트 차별화 (무료 1주 / 유료 전 기간) — 후속</li>
 *   <li>단계 4 데이터 라이선싱 (B2B 가격 시계열) — 12개월 누적 후</li>
 * </ul>
 * 자세한 건 docs/monetization.md / docs/plan.md § 수익화 계획 참조.</p>
 */
@Entity
@Getter
@Table(
        name = "ingredient_price_history",
        indexes = @Index(name = "idx_history_ingredient_recorded",
                columnList = "ingredient_id, recordedAt DESC")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IngredientPriceHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ingredient_id", nullable = false,
            foreignKey = @jakarta.persistence.ForeignKey(name = "fk_history_ingredient"))
    private Ingredient ingredient;

    @Column(name = "naver_product_id", nullable = false, length = 64)
    private String naverProductId;

    @Column(nullable = false)
    private int price;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 4)
    private Unit unit;

    @Column(name = "price_per_gram", nullable = false, precision = 19, scale = 4)
    private BigDecimal pricePerGram;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    @Builder
    private IngredientPriceHistory(Ingredient ingredient, String naverProductId, int price,
                                   BigDecimal totalAmount, Unit unit, BigDecimal pricePerGram,
                                   LocalDateTime recordedAt) {
        this.ingredient = ingredient;
        this.naverProductId = naverProductId;
        this.price = price;
        this.totalAmount = totalAmount;
        this.unit = unit;
        this.pricePerGram = pricePerGram;
        this.recordedAt = recordedAt;
    }

    /**
     * Ingredient 현재 상태를 스냅샷으로 기록. 호출은 IngredientService에서만.
     */
    public static IngredientPriceHistory snapshotOf(Ingredient ingredient, LocalDateTime recordedAt) {
        return IngredientPriceHistory.builder()
                .ingredient(ingredient)
                .naverProductId(ingredient.getNaverProductId())
                .price(ingredient.getPrice())
                .totalAmount(ingredient.getTotalAmount())
                .unit(ingredient.getUnit())
                .pricePerGram(ingredient.getPricePerGram())
                .recordedAt(recordedAt)
                .build();
    }
}
