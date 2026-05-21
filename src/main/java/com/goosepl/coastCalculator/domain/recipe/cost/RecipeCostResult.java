package com.goosepl.coastCalculator.domain.recipe.cost;

import com.goosepl.coastCalculator.domain.ingredient.Unit;

import java.math.BigDecimal;
import java.util.List;

public record RecipeCostResult(
        List<IngredientCostLine> lines,
        BigDecimal totalCost,
        BigDecimal costPerServing,
        int missingCount,
        PricingPolicy policy
) {

    public boolean hasMissing() {
        return missingCount > 0;
    }

    public record IngredientCostLine(
            String categoryName,
            BigDecimal amount,
            Unit unit,
            BigDecimal pricePerUnit,
            BigDecimal subtotal,
            int matchedCandidateCount,
            boolean missing
    ) {
    }
}
