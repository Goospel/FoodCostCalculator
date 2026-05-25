package com.goosepl.coastCalculator.domain.ingredient;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface IngredientPriceHistoryRepository extends JpaRepository<IngredientPriceHistory, Long> {

    /** 특정 ingredient의 가격 이력 — 최신순 정렬. admin/history.html에서 사용. */
    List<IngredientPriceHistory> findByIngredientIdOrderByRecordedAtDesc(Long ingredientId);

    /** 특정 ingredient의 기간 조회 (후속: 차트/리포트). */
    List<IngredientPriceHistory> findByIngredientIdAndRecordedAtBetweenOrderByRecordedAtAsc(
            Long ingredientId, LocalDateTime from, LocalDateTime to);
}
