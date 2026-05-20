package com.goosepl.coastCalculator.domain.recipe.dto;

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

    public boolean isEmpty() {
        return (categoryName == null || categoryName.isBlank()) && amount == null;
    }

    public static RecipeIngredientForm from(RecipeIngredient ri) {
        RecipeIngredientForm f = new RecipeIngredientForm();
        f.categoryName = ri.getCategoryName();
        f.amount = ri.getAmount();
        f.unit = ri.getUnit();
        return f;
    }
}
