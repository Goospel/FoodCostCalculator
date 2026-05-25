-- =========================================================
-- V4: 식재료 가격 이력 (T3-19, 2026-05-25)
--
-- 정책 (옵션 B — 사용자 결정): 가격 변동 시에만 row 적재.
--   - 신규 ingredient: 첫 fetch 시 무조건 적재 (시작 시점)
--   - 기존 ingredient: pricePerGram(BigDecimal.compareTo != 0) 변동 시에만 적재
--   → 데이터 효율 + "이 시점에 가격 변동" 의미 명확 + 차트에 적합 (계단형)
--
-- 노출 (옵션 X): 현재는 admin만(`/admin/ingredients/{id}/history`).
-- 일반 사용자 공개는 수익화 단계 3(Freemium) 도입 시 무료/유료 라인 결정 후.
--
-- 관계:
--   - ingredient_id FK → ingredients(id) ON DELETE CASCADE
--     ingredient 삭제 시 이력도 함께 삭제 (admin 명시적 액션이라 자연스러움)
--   - naver_product_id snapshot — ingredient 삭제 후에도 추적 가능 (감사용)
--
-- 인덱스:
--   - (ingredient_id, recorded_at DESC) — 특정 ingredient 최근 N개 추이 조회 빠름
--
-- 백필:
--   - 기존 ingredients의 현재 가격을 첫 history row로 시드
--   - recorded_at = ingredients.fetched_at 그대로 (실제 마지막 fetch 시점)
--
-- 표준 SQL — MySQL/H2 모두 호환.
-- =========================================================

CREATE TABLE `ingredient_price_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `ingredient_id` bigint NOT NULL,
  `naver_product_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `price` int NOT NULL,
  `total_amount` decimal(19,4) NOT NULL,
  `unit` varchar(4) COLLATE utf8mb4_unicode_ci NOT NULL,
  `price_per_gram` decimal(19,4) NOT NULL,
  `recorded_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_history_ingredient_recorded` (`ingredient_id`, `recorded_at` DESC),
  CONSTRAINT `fk_history_ingredient`
    FOREIGN KEY (`ingredient_id`)
    REFERENCES `ingredients` (`id`)
    ON DELETE CASCADE
    ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 기존 ingredients를 첫 history row로 백필 (멱등 — V4 한 번만 실행됨)
INSERT INTO `ingredient_price_history`
  (`ingredient_id`, `naver_product_id`, `price`, `total_amount`, `unit`, `price_per_gram`, `recorded_at`)
SELECT
  `id`, `naver_product_id`, `price`, `total_amount`, `unit`, `price_per_gram`, `fetched_at`
FROM `ingredients`;
