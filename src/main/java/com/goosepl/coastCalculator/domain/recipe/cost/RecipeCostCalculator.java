package com.goosepl.coastCalculator.domain.recipe.cost;

import com.goosepl.coastCalculator.domain.ingredient.Ingredient;
import com.goosepl.coastCalculator.domain.ingredient.IngredientRepository;
import com.goosepl.coastCalculator.domain.recipe.Recipe;
import com.goosepl.coastCalculator.domain.recipe.RecipeIngredient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RecipeCostCalculator {

    private static final int PRICE_PER_UNIT_SCALE = 4;

    private final IngredientRepository ingredientRepository;

    @Transactional(readOnly = true)
    public RecipeCostResult calculate(Recipe recipe, PricingPolicy policy) {
        PricingPolicy effectivePolicy = policy != null ? policy : PricingPolicy.LOWEST;

        List<RecipeCostResult.IngredientCostLine> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int missingCount = 0;

        for (RecipeIngredient ri : recipe.getIngredients()) {
            // 1) 사용자가 특정 제품을 명시적으로 선택한 경우 → 그 제품의 단가 사용 (정책 무시)
            if (ri.getSelectedIngredient() != null) {
                BigDecimal pricePerUnit = ri.getSelectedIngredient().getPricePerGram();
                BigDecimal subtotal = roundWon(pricePerUnit.multiply(ri.getAmount()));
                total = total.add(subtotal);
                lines.add(new RecipeCostResult.IngredientCostLine(
                        ri.getCategoryName(), ri.getAmount(), ri.getUnit(),
                        pricePerUnit, subtotal, 1, false));
                continue;
            }

            // 2) 카테고리 + 단위로 후보 검색
            List<Ingredient> candidates = ingredientRepository.findByCategoryAndUnit(
                    ri.getCategoryName(), ri.getUnit());

            if (candidates.isEmpty()) {
                lines.add(new RecipeCostResult.IngredientCostLine(
                        ri.getCategoryName(), ri.getAmount(), ri.getUnit(),
                        null, null, 0, true));
                missingCount++;
                continue;
            }

            // 3) 정책에 따라 단가 결정
            BigDecimal pricePerUnit = applyPolicy(candidates, effectivePolicy);
            BigDecimal subtotal = roundWon(pricePerUnit.multiply(ri.getAmount()));
            total = total.add(subtotal);

            lines.add(new RecipeCostResult.IngredientCostLine(
                    ri.getCategoryName(), ri.getAmount(), ri.getUnit(),
                    pricePerUnit, subtotal, candidates.size(), false));
        }

        BigDecimal perServing = BigDecimal.ZERO;
        if (recipe.getServings() > 0) {
            perServing = total.divide(
                    BigDecimal.valueOf(recipe.getServings()), 0, RoundingMode.HALF_UP);
        }

        return new RecipeCostResult(lines, total, perServing, missingCount, effectivePolicy);
    }

    private BigDecimal applyPolicy(List<Ingredient> candidates, PricingPolicy policy) {
        List<BigDecimal> prices = candidates.stream()
                .map(Ingredient::getPricePerGram)
                .toList();
        return switch (policy) {
            case LOWEST -> prices.stream().min(Comparator.naturalOrder()).orElseThrow();
            case HIGHEST -> prices.stream().max(Comparator.naturalOrder()).orElseThrow();
            case AVERAGE -> {
                BigDecimal sum = prices.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                yield sum.divide(BigDecimal.valueOf(prices.size()),
                        PRICE_PER_UNIT_SCALE, RoundingMode.HALF_UP);
            }
        };
    }

    private static BigDecimal roundWon(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP);
    }
}
