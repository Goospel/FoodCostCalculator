-- =========================================================
-- V2: 카테고리 마스터 테이블 (T3-18, 2026-05-25)
--
-- ingredients.category 자유 입력으로 인한 정합성 의존(예: 밀가루 vs 박력분)을
-- 점진적으로 줄이기 위한 마스터 목록. Ingredient.category는 String 그대로 유지하고
-- (마이그레이션 부담↓), 폼에서 <datalist> 자동완성으로 권장 카테고리만 유도.
--
-- alias/synonym 매핑은 별도 후속(T3-18.2). 이번 V2는 마스터 + 시드만.
--
-- 표준 SQL로 작성 — MySQL/H2 모두 호환 (NOT IN 패턴, NOW(6), AUTO_INCREMENT는
-- MySQL 전용이지만 H2도 동일 키워드 지원).
-- =========================================================

CREATE TABLE `categories` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_categories_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 기존 ingredients의 category 값들을 마스터로 시드 (멱등 — NOT IN 패턴)
INSERT INTO `categories` (`name`, `created_at`)
SELECT DISTINCT `category`, NOW(6)
FROM `ingredients`
WHERE `category` IS NOT NULL
  AND `category` <> ''
  AND `category` NOT IN (SELECT `name` FROM `categories`);
