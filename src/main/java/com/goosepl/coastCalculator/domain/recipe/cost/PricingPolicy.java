package com.goosepl.coastCalculator.domain.recipe.cost;

public enum PricingPolicy {
    LOWEST,
    AVERAGE,
    HIGHEST;

    public String label() {
        return switch (this) {
            case LOWEST -> "최저가";
            case AVERAGE -> "평균";
            case HIGHEST -> "최고가";
        };
    }
}
