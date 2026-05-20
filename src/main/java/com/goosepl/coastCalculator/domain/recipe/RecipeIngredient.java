package com.goosepl.coastCalculator.domain.recipe;

import com.goosepl.coastCalculator.domain.ingredient.Ingredient;
import com.goosepl.coastCalculator.domain.ingredient.Unit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Getter
@Table(name = "recipe_ingredients")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecipeIngredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;

    @Column(nullable = false, length = 100)
    private String categoryName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_ingredient_id")
    private Ingredient selectedIngredient;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 4)
    private Unit unit;

    @Column(nullable = false)
    private int ordering;

    @Builder
    private RecipeIngredient(String categoryName, Ingredient selectedIngredient,
                             BigDecimal amount, Unit unit, int ordering) {
        this.categoryName = categoryName;
        this.selectedIngredient = selectedIngredient;
        this.amount = amount;
        this.unit = unit;
        this.ordering = ordering;
    }

    void assignRecipe(Recipe recipe) {
        this.recipe = recipe;
    }
}
