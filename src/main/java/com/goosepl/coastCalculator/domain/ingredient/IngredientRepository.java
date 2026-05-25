package com.goosepl.coastCalculator.domain.ingredient;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface IngredientRepository extends JpaRepository<Ingredient, Long> {

    Optional<Ingredient> findByNaverProductId(String naverProductId);

    List<Ingredient> findByCategory(String category);

    List<Ingredient> findByCategoryAndUnit(String category, Unit unit);

    List<Ingredient> findByCategoryIsNotNullOrderByCategoryAscPricePerGramAsc();

    List<Ingredient> findAllByOrderByCategoryAscPricePerGramAsc();

    /**
     * T2-8: stale row를 가진 카테고리들 distinct 추출. 스케줄러가 이 결과로 카테고리별 refetch 트리거.
     * - category IS NULL은 제외 (사용자에게 노출 안 되는 row)
     * - fetched_at &lt; threshold 인 row가 하나라도 있는 카테고리 반환
     * - 결과 카디널리티는 카테고리 수와 같아 가벼움
     */
    @Query("""
            select distinct i.category
            from Ingredient i
            where i.category is not null
              and i.fetchedAt < :threshold
            """)
    List<String> findDistinctStaleCategoriesBefore(@Param("threshold") LocalDateTime threshold);
}
