package com.goosepl.coastCalculator.domain.category;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * T3-18.2: 카테고리 alias/synonym.
 *
 * 정책 (옵션 A): 매칭 단계에서만 풀고 저장은 그대로 — T3-17 "사용자 의도 보존"과 일관.
 *   예) RecipeIngredient.categoryName = "박력분"으로 저장
 *       RecipeCostCalculator에서 ingredient 매칭 시 resolve("박력분") → "밀가루"로 검색
 *
 * 제약:
 *   - alias는 unique (DB 제약)
 *   - alias는 categories.name과 disjoint해야 함 (코드 레벨 검증; CategoryAliasService.add)
 *   - canonical FK CASCADE — canonical 카테고리 삭제 시 alias도 자동 삭제
 */
@Entity
@Getter
@Table(
        name = "category_aliases",
        uniqueConstraints = @UniqueConstraint(name = "uk_category_aliases_alias", columnNames = "alias")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CategoryAlias {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String alias;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "canonical_category_id", nullable = false,
            foreignKey = @jakarta.persistence.ForeignKey(name = "fk_category_aliases_canonical"))
    private Category canonical;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private CategoryAlias(String alias, Category canonical) {
        this.alias = alias;
        this.canonical = canonical;
    }
}
