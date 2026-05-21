-- =========================================================
-- V1: 초기 스키마 동결 (2026-05-21)
--
-- 이 시점까지 ddl-auto: update 로 Hibernate가 누적 생성한 스키마를
-- mysqldump --no-data 로 추출하여 Flyway 마이그레이션의 baseline 으로 동결한다.
--
-- FK / unique key 이름은 Hibernate 자동 생성값 그대로 유지 — 기존 운영 DB와
-- 충돌 회피 (baseline-on-migrate 적용 시 이 V1은 실행되지 않고 history에만 기록됨).
--
-- 향후 스키마 변경은 V2__xxx.sql 로 추가, ddl-auto 는 validate 로 고정.
-- =========================================================

-- ---------------------------------------------------------
-- users — 회원 (ROLE_USER / ROLE_ADMIN)
-- ---------------------------------------------------------
CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `password` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `role` enum('ADMIN','USER') COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `username` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKr43af9ap4edm43mmtq01oddj6` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------
-- ingredients — Naver Shopping fetch 결과 + 관리자 카테고리
-- ---------------------------------------------------------
CREATE TABLE `ingredients` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `category` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `fetched_at` datetime(6) NOT NULL,
  `image` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `link` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `mall_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `naver_product_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `price` int NOT NULL,
  `price_per_gram` decimal(19,4) NOT NULL,
  `title` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `total_amount` decimal(19,4) NOT NULL,
  `unit` enum('G','ML') COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_ingredients_naver_product_id` (`naver_product_id`),
  KEY `idx_ingredients_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------
-- recipes — 사용자 작성 레시피
-- ---------------------------------------------------------
CREATE TABLE `recipes` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `image_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `servings` int NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKlc3x6yty3xsupx80hqbj9ayos` (`user_id`),
  CONSTRAINT `FKlc3x6yty3xsupx80hqbj9ayos` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------
-- recipe_ingredients — 레시피의 재료 행 (카테고리/양/단위 + 선택 Ingredient FK)
-- ---------------------------------------------------------
CREATE TABLE `recipe_ingredients` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `amount` decimal(19,4) NOT NULL,
  `category_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `ordering` int NOT NULL,
  `unit` enum('G','ML') COLLATE utf8mb4_unicode_ci NOT NULL,
  `recipe_id` bigint NOT NULL,
  `selected_ingredient_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKcqlw8sor5ut10xsuj3jnttkc` (`recipe_id`),
  KEY `FK3ud4y09mh4ec6tisg9wa11exa` (`selected_ingredient_id`),
  CONSTRAINT `FKcqlw8sor5ut10xsuj3jnttkc` FOREIGN KEY (`recipe_id`) REFERENCES `recipes` (`id`),
  CONSTRAINT `FK3ud4y09mh4ec6tisg9wa11exa` FOREIGN KEY (`selected_ingredient_id`) REFERENCES `ingredients` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------
-- recipe_likes — 좋아요 (recipe × user 유일성)
-- ---------------------------------------------------------
CREATE TABLE `recipe_likes` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `recipe_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_recipe_like_recipe_user` (`recipe_id`,`user_id`),
  KEY `FK3h43xv6xr4dr42hpl8xym711d` (`user_id`),
  CONSTRAINT `FK123rwitbkijue4y39x2a9pqqb` FOREIGN KEY (`recipe_id`) REFERENCES `recipes` (`id`),
  CONSTRAINT `FK3h43xv6xr4dr42hpl8xym711d` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------
-- comments — 댓글 + 1단계 대댓글 (parent self-FK)
-- ---------------------------------------------------------
CREATE TABLE `comments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `content` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `parent_id` bigint DEFAULT NULL,
  `recipe_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKlri30okf66phtcgbe5pok7cc0` (`parent_id`),
  KEY `FKdtb5nfo2c69a6chahuihyaqx` (`recipe_id`),
  KEY `FK8omq0tc18jd43bu5tjh6jvraq` (`user_id`),
  CONSTRAINT `FKlri30okf66phtcgbe5pok7cc0` FOREIGN KEY (`parent_id`) REFERENCES `comments` (`id`),
  CONSTRAINT `FKdtb5nfo2c69a6chahuihyaqx` FOREIGN KEY (`recipe_id`) REFERENCES `recipes` (`id`),
  CONSTRAINT `FK8omq0tc18jd43bu5tjh6jvraq` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
