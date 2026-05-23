package com.goosepl.coastCalculator.domain.recipe.dto;

import com.goosepl.coastCalculator.domain.ingredient.Ingredient;
import com.goosepl.coastCalculator.domain.ingredient.Unit;
import com.goosepl.coastCalculator.domain.recipe.RecipeIngredient;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class RecipeIngredientForm {

    private String categoryName;
    private BigDecimal amount;
    private Unit unit = Unit.G;
    /**
     * T3-17: 사용자가 "이 제품으로 고정"으로 특정 Ingredient를 선택한 경우의 FK.
     * 비어있으면 정책(LOWEST/AVERAGE/HIGHEST)에 따라 자동 매칭.
     */
    private Long selectedIngredientId;

    public boolean isEmpty() {
        // categoryName과 amount가 모두 비어 있으면 빈 행으로 간주 (단순히 selectedIngredientId만 있는 것은 잘못된 입력이므로 빈 행 아님)
        return (categoryName == null || categoryName.isBlank()) && amount == null;
    }

    public static RecipeIngredientForm from(RecipeIngredient ri) {
        RecipeIngredientForm f = new RecipeIngredientForm();
        f.categoryName = ri.getCategoryName();
        f.amount = ri.getAmount();
        f.unit = ri.getUnit();
        Ingredient selected = ri.getSelectedIngredient();
        f.selectedIngredientId = (selected != null) ? selected.getId() : null;
        return f;
    }
}
