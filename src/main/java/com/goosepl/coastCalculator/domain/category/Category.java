package com.goosepl.coastCalculator.domain.category;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * T3-18: 식재료 카테고리 마스터.
 *
 * Ingredient.category(String)와는 별도로, 폼 datalist 자동완성과 admin 권장 목록을 위한 마스터.
 * Ingredient에서 FK로 강결합하지 않는 이유: 점진적 도입 + 기존 자유 입력 코드(특히 RecipeIngredient.categoryName)와 자연 결합.
 * alias/synonym(예: 박력분 → 밀가루) 매핑은 별도 후속 (T3-18.2).
 */
@Entity
@Getter
@Table(
        name = "categories",
        uniqueConstraints = @UniqueConstraint(name = "uk_categories_name", columnNames = "name")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private Category(String name) {
        this.name = name;
    }
}
