-- =========================================================
-- V5: recipes 테이블에 optimistic locking 버전 컬럼 추가 (T2-12, 2026-05-25)
--
-- @Version 필드 — Hibernate가 UPDATE 시 version 일치 확인 후 +1.
-- 동시 편집 충돌: 두 트랜잭션이 같은 version 로드 후 각자 수정 → 두 번째 save 시 mismatch →
-- ObjectOptimisticLockingFailureException → GlobalExceptionHandler가 사용자 친화 메시지로 폼 재렌더.
--
-- 정책:
--   - Recipe만 @Version 부여 — 가장 충돌 빈도 높은 엔티티 (사용자 본인이 두 탭 편집)
--   - RecipeIngredient는 별도 안 부여 — Recipe @Version의 cascade UPDATE로 함께 보호됨
--   - Ingredient는 admin만 카테고리 단일 필드 만지고 충돌 가능성 낮아 의도적 비포함
--
-- DEFAULT 0 — 기존 row들의 시작 version. NOT NULL 강제.
-- =========================================================

ALTER TABLE `recipes`
  ADD COLUMN `version` bigint NOT NULL DEFAULT 0;
