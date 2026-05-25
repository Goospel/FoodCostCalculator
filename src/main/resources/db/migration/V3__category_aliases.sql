-- =========================================================
-- V3: 카테고리 alias/synonym 테이블 (T3-18.2, 2026-05-25)
--
-- 박력분 → 밀가루 같은 유의어를 canonical 카테고리로 풀어주는 매핑.
-- 정책: 매칭 단계에서만 정규화(저장은 그대로) — T3-17의 "사용자 의도 보존" 정책과 일관.
--
-- 관계:
--   - alias: unique varchar — 사용자가 입력하는 비표준 카테고리 이름
--   - canonical_category_id: categories.id FK — 어느 표준 카테고리로 풀지
--   - alias는 categories.name과 disjoint 해야 함 (코드 레벨에서 검증, DB 제약은 안 검)
--
-- canonical 카테고리가 삭제되면 alias도 같이 삭제 (ON DELETE CASCADE).
--
-- 표준 SQL — MySQL/H2 모두 호환.
-- =========================================================

CREATE TABLE `category_aliases` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `alias` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `canonical_category_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_category_aliases_alias` (`alias`),
  KEY `ix_category_aliases_canonical` (`canonical_category_id`),
  CONSTRAINT `fk_category_aliases_canonical`
    FOREIGN KEY (`canonical_category_id`)
    REFERENCES `categories` (`id`)
    ON DELETE CASCADE
    ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
