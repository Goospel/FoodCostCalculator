package com.goosepl.coastCalculator.domain.ingredient;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "ingredients",
        indexes = {
                @Index(name = "idx_ingredients_category", columnList = "category"),
                @Index(name = "idx_ingredients_naver_product_id", columnList = "naverProductId", unique = true)
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Ingredient {

    private static final int PRICE_PER_UNIT_SCALE = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String naverProductId;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(length = 100)
    private String category;

    @Column(nullable = false)
    private int price;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 4)
    private Unit unit;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal pricePerGram;

    @Column(length = 500)
    private String image;

    @Column(length = 100)
    private String mallName;

    @Column(length = 500)
    private String link;

    @Column(nullable = false)
    private LocalDateTime fetchedAt;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Builder
    private Ingredient(String naverProductId, String title, String category, int price,
                       BigDecimal totalAmount, Unit unit, String image, String mallName,
                       String link, LocalDateTime fetchedAt) {
        this.naverProductId = naverProductId;
        this.title = title;
        this.category = category;
        this.price = price;
        this.totalAmount = totalAmount;
        this.unit = unit;
        this.image = image;
        this.mallName = mallName;
        this.link = link;
        this.fetchedAt = fetchedAt;
        this.pricePerGram = computePricePerUnit(price, totalAmount);
    }

    public void refreshFromNaver(String title, int price, BigDecimal totalAmount, Unit unit,
                                 String image, String mallName, String link, LocalDateTime fetchedAt) {
        this.title = title;
        this.price = price;
        this.totalAmount = totalAmount;
        this.unit = unit;
        this.image = image;
        this.mallName = mallName;
        this.link = link;
        this.fetchedAt = fetchedAt;
        this.pricePerGram = computePricePerUnit(price, totalAmount);
    }

    public void updateCategory(String category) {
        this.category = category;
    }

    private static BigDecimal computePricePerUnit(int price, BigDecimal totalAmount) {
        if (totalAmount == null || totalAmount.signum() <= 0) {
            throw new IllegalArgumentException("totalAmount는 0보다 커야 합니다");
        }
        return BigDecimal.valueOf(price).divide(totalAmount, PRICE_PER_UNIT_SCALE, RoundingMode.HALF_UP);
    }
}
