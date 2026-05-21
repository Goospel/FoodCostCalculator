package com.goosepl.coastCalculator.domain.ingredient;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IngredientRepository extends JpaRepository<Ingredient, Long> {

    Optional<Ingredient> findByNaverProductId(String naverProductId);

    List<Ingredient> findByCategory(String category);

    List<Ingredient> findByCategoryAndUnit(String category, Unit unit);

    List<Ingredient> findByCategoryIsNotNullOrderByCategoryAscPricePerGramAsc();

    List<Ingredient> findAllByOrderByCategoryAscPricePerGramAsc();
}
