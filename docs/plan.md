# coastCalculator — 작업 계획 / 진행 상태

안정적인 아키텍처·도메인·규칙은 [CLAUDE.md](../CLAUDE.md) 참조. 트러블슈팅 기록은 [troubleshooting.md](troubleshooting.md), 배포 readiness 백로그는 [improvements.md](improvements.md). 이 문서는 **Task 진행 상태, Task별 상세, Open Questions, Verification 체크리스트** 등 진행하면서 갱신되는 내용만 담음.

---

# 구현 순서 / 진행 상태

| # | 단계 | 상태 |
|---|---|:---:|
| 1 | 인프라 (`application.yaml`, `SecurityConfig`, MySQL Docker) | ✅ |
| 2 | User 도메인 (엔티티 + 회원가입/로그인) | ✅ |
| 3 | Ingredient 도메인 + Naver API + admin seed + 권한 분리 (Plan v3) | ✅ |
| 4 | Recipe 도메인 (단순 CRUD) | ✅ |
| 4.5 | Naver API 키 적용 (`application-local.yaml` + local 프로파일) | ✅ |
| 4.7 | 레시피 허브 (공개 조회 + 검색 + 홈 페이지 전환) | ✅ |
| 5 | 원가 계산 + `PricingPolicy` + detail/edit 페이지 분리 | ✅ |
| 6 | 마무리 — 글로벌 예외 처리 + 커스텀 에러 페이지 (T1-5) | ✅ |
| 7 | 커뮤니티 (좋아요 + 댓글/대댓글) + 레시피 이미지 업로드 (T3-15/16) | ✅ |
| Stage A | 배포 준비 — admin 비번 외부화(T1-2) + Dockerfile(T3-22) + 제휴 링크 + EC2 가이드 | ✅ |
| T1-1 | Flyway 도입 (V1 baseline + `ddl-auto: validate`) | ✅ |
| T1-3 | 비밀번호 정책 (8자 영숫자) + brute force 방어 (5회/15분) | ✅ |
| **다음** | **T1-6 핵심 통합 테스트 3종** (또는 T2-9 외부 호출 타임아웃) | ⏳ |

---

# Task 3: Ingredient 도메인 + Naver API + admin seed + 권한 분리

- 3-a. `DataInitializer` — admin 시드 (멱등)
- 3-b. `SecurityConfig` — `/admin/**` → `hasRole("ADMIN")`, `/ingredients` → `authenticated()`
- 3-c. `application.yaml`의 `naver:` 블록 — TTL 24h
- 3-d. `Ingredient` 엔티티 + `Unit` enum + `IngredientRepository`
- 3-e. `UnitParser` — 정규식 기반 (단위 테스트 우선)
- 3-f. `NaverShoppingClient` 인터페이스 + Mock + Real (RestClient)
- 3-g. `IngredientService` — fetch/upsert, 카테고리 수정/삭제/조회
- 3-h. `AdminIngredientController` + Thymeleaf
- 3-i. `IngredientController` (사용자 read-only) + 리스트
- 3-j. `home.html` 네비게이션 admin 메뉴

---

# Task 4: Recipe 도메인 (단순 CRUD)

- 4-a. `Recipe`, `RecipeIngredient` 엔티티 + Repository
- 4-b. `RecipeService` — 본인 레시피만 (소유자 체크)
- 4-c. `RecipeForm` DTO — 폼 바인딩 (재료 행 10개)
- 4-d. `RecipeController` — `/recipes` 목록 / `/new` / `/{id}` 조회·수정 / `/delete`
- 4-e. Thymeleaf — list, form (고정 10행, JS 없음)

---

# Task 4.5: Naver API 키 적용

- `src/main/resources/application-local.yaml`에 `client-id` / `client-secret` / `mock-enabled: false`
- `.gitignore`에 `application-local.yaml` 포함
- 실행: `./gradlew bootRun --args='--spring.profiles.active=local'`

---

# Task 4.7: 레시피 허브

- 단순 "내 원가 계산기" → 레시피 공유 허브
- `/` 랜딩에 공개 레시피 목록 + 검색바
- 익명에게 홈/상세/검색 공개, 작성/수정/삭제만 인증
- `home.html` 전면 개편, `recipes/detail.html` 신규

---

# Task 5: 원가 계산

- `PricingPolicy` enum (LOWEST / AVERAGE / HIGHEST)
- `IngredientRepository.findByCategoryAndUnit(String, Unit)`
- `RecipeCostCalculator` + `RecipeCostResult` DTO
- detail/edit 페이지 분리:
  - `GET /recipes/{id}` → `detail.html` (read-only + 원가 + Policy 드롭다운)
  - `GET /recipes/{id}/edit` → `form.html` (편집)
  - `POST /recipes/{id}` → 업데이트 후 detail로 redirect

---

# Task 6: 마무리 — 글로벌 예외 처리

`improvements.md` T1-5와 동반 해결.

- `web/error/GlobalExceptionHandler.java` (`@ControllerAdvice`)
- `templates/error/{403,404,500,error}.html`
- `server.error.include-message: never` (운영 안전성)

---

# Task 7: 커뮤니티 + 이미지 업로드

`improvements.md` T3-15 / T3-16 부분 해결.

## 7-a. 도메인
- `Recipe.imageUrl` 컬럼 추가 (nullable, length=255)
- `RecipeLike` 신규 — `(recipe_id, user_id)` unique 제약
- `Comment` 신규 — self-FK `parent` (1단계 대댓글)
- Recipe에 `likes`, `comments` OneToMany 추가

## 7-b. 이미지 스토리지
- `ImageStorageService` 인터페이스 + `LocalImageStorageService` (UUID 파일명, 확장자 화이트리스트, 5MB 제한, 디렉토리 트래버설 방지)
- `StorageProperties` (record-based `@ConfigurationProperties`)
- `WebMvcConfig` — `/uploads/**` → 로컬 파일시스템 매핑
- `application.yaml`에 `spring.servlet.multipart.*` + `storage.upload-dir`
- `.gitignore`에 `uploads/`

## 7-c. 좋아요
- `RecipeLikeService.toggle/count/isLikedBy`
- `LikeController` — `POST /recipes/{id}/like`
- detail.html 좋아요 섹션 (인증 시 버튼, 익명은 카운트만)

## 7-d. 댓글 + 1단계 대댓글
- `CommentService.create/delete/listForRecipe` (1쿼리 fetch 후 그루핑)
- `RootCommentView` / `ReplyView` DTO
- `CommentController` — `POST /recipes/{id}/comments`, `POST /recipes/{rid}/comments/{cid}/delete`
- 작성자 또는 ADMIN만 삭제 (cascade로 대댓글 동반 삭제)
- detail.html 댓글 섹션

## 7-e. 이미지 폼 통합
- `RecipeService.create/update`에 `MultipartFile` + `removeImage` 인자
- form.html `enctype="multipart/form-data"` + 파일 input + 기존 이미지 미리보기/삭제 체크박스
- home.html 카드에 썸네일 (또는 placeholder 🍳)

---

# Stage A: 배포 준비

EC2 비공개 베타용 준비 단계. 실제 EC2 배포는 보류 (사용자가 다른 프로젝트 우선).

## Stage A-1. T1-2 admin 비번 환경변수화
- `DataInitializer`에서 코드 하드코딩 제거
- `app.admin.initial-password` (env `INITIAL_ADMIN_PASSWORD`) 우선
- 미설정 시 `SecureRandom` 16자 랜덤 + 부팅 WARN 로그 1회 출력
- 로컬 개발은 `application-local.yaml`에 `admin123!!` 고정

## Stage A-2. T3-22 Dockerfile + 운영용 docker-compose
- **Dockerfile** — 멀티스테이지 (JDK 25 builder → JRE 25 runtime, 비루트 user, `JAVA_OPTS` env)
- **docker-compose.prod.yml** — 앱 + MySQL 한 머신, 볼륨(`coast-uploads`, `coast-mysql-data`), healthcheck
- **`.env.prod.example`** + **`.dockerignore`** + `.gitignore` 갱신
- `application.yaml`의 `naver.api.mock-enabled` 를 `NAVER_MOCK_ENABLED` env로 override

## Stage A-3. 제휴 링크 wrap (쿠팡 파트너스)
- `AffiliateProperties` (record-based) + `AffiliateLinkBuilder`
- `application.yaml`에 `affiliate.coupang.tracking-id` (env `COUPANG_TRACKING_ID`)
- `ingredients/list.html`에 "쿠팡 검색" 컬럼 추가 (tracking-id 비어 있으면 일반 검색 URL)

## Stage A-4. EC2 배포 가이드
- `docs/deployment.md` 작성 — EC2 생성부터 admin 비번 회수까지 9개 섹션
- 무료 티어 t3.micro 기준, 도메인/HTTPS 없이 IP 직접 접속

---

# T1-1: Flyway 도입

`improvements.md` T1-1 해결.

- **의존성**: `spring-boot-starter-flyway` + `flyway-mysql`
  ※ Spring Boot 4부터 autoconfigure 모듈 분리 — `flyway-core` 단독 X (자세한 내용은 [troubleshooting.md TS-1](troubleshooting.md))
- **V1**: `src/main/resources/db/migration/V1__init_schema.sql`
  - `mysqldump --no-data`로 현재 스키마 추출 → CREATE TABLE 6개
  - Hibernate가 만든 FK/unique 이름 그대로 유지 (기존 DB 충돌 방지)
- **application.yaml**:
  - `spring.flyway.baseline-on-migrate: true`
  - `spring.flyway.baseline-version: 1` (기존 DB는 V1을 baseline 마크만, 실행 X)
  - `spring.jpa.hibernate.ddl-auto: update` → **`validate`**
- **검증**: `flyway_schema_history` 테이블 생성, V1이 BASELINE 타입으로 기록, 부팅 7.75s 정상

---

# T1-3: 비밀번호 정책 + brute force 방어

`improvements.md` T1-3 해결.

## 정책
- **비밀번호 강도**: 최소 8자 + 영문/숫자 필수 (`@Pattern("(?=.*[A-Za-z])(?=.*\\d).+")`)
- **잠금**: 같은 username 5회 연속 실패 → 15분 잠금 (메모리 카운터, DB 컬럼 X)
- **해제**: 마지막 실패 시각 + 15분 경과 시 자동 해제 — 관리자 개입 불필요

## 구현
- **신규**: `domain/user/auth/LoginAttemptService.java` — `ConcurrentHashMap<String, Attempt>` 기반 카운터. `MAX_ATTEMPTS=5`, `LOCK_DURATION=15min` 상수. `recordFailure / recordSuccess / isBlocked` API.
- **신규**: `domain/user/auth/AuthenticationEventListener.java` — Spring Security가 발행하는 `AuthenticationFailureBadCredentialsEvent` 구독 → 카운터 +1, `AuthenticationSuccessEvent` 구독 → 카운터 리셋. Spring Boot가 기본으로 `DefaultAuthenticationEventPublisher` 등록함.
- **수정**: `domain/user/dto/SignupRequest.java` — `@Size(min=4)` → `@Size(min=8)` + `@Pattern`. signup.html에 정책 안내 한 줄.
- **수정**: `domain/user/CustomUserDetailsService.java` — `LoginAttemptService` 주입, `.accountLocked(isBlocked(username))`로 반환 → 잠금 상태에서 시도 시 Spring이 `LockedException` 발생.
- **수정**: `config/SecurityConfig.java` — `AuthenticationFailureHandler` 빈 추가: `LockedException`이면 `/login?locked`, 그 외(`BadCredentials` 등)는 `/login?error` 분기.
- **수정**: `templates/login.html` — `?locked` 분기 메시지 ("15분 후 다시 시도").

## 검증
- 컴파일 / 부팅 정상 (`./gradlew bootRun` 에러 없음)
- 매뉴얼 시나리오 통과 — 아래 Verification "T1-3 — 완료" 참조

## Out of Scope
- DB 잠금 필드 (`User.failedAttempts`, `lockedUntil`) — V2 마이그레이션 회피
- IP 기반 차단 (현재 username 기반) — Bucket4j 도입 시
- 비번 재설정 / HaveIBeenPwned / captcha — 별도 항목

---

# Open Questions — 확정된 답변

| # | 질문 | 답변 |
|---|---|---|
| 1 | 네이버 API 키 | 미발급 → Mock으로 개발, 키 받으면 yaml 토글 |
| 2 | MySQL 환경 | Docker — 호스트 3309 ↔ 컨테이너 3306, 스키마 `coast_calculator` |
| 3 | 카테고리 관리 | 관리자가 개별 제품마다 수동 지정 |
| 4 | 원가 표시 | 전체 + 인분당 둘 다 |
| 5 | 재료 선택 정책 | 기본 LOWEST, AVERAGE/HIGHEST 토글 |
| 6 | 관리자 워크플로 | Naver fetch로 raw 받은 뒤 admin UI에서 row별 카테고리 입력 |
| 7 | 관리자 시드 | `admin` (`ROLE_ADMIN`) 멱등. 비번은 env 또는 랜덤 fallback (Stage A-1에서 변경) |
| 8 | Naver fetch 중복 | `naverProductId` 존재 시 다른 필드만 업데이트, `category` 보존 |
| 9 | TTL | 24시간, 사용자 조회 시 stale 자동 refetch |
| 10 | Recipe 재료 행 UI | 고정 10행, 빈 행 저장 시 무시, JS 없음 |
| 11 | RecipeIngredient 단위 | `amount` + `unit` 분리 |
| 12 | detail vs edit | 분리 — detail은 read-only + 원가 + Policy, edit은 별도 폼 |
| 13 | 매칭 실패 | 0건 시 "재료 없음", 총합 제외, 경고 카운트 |
| 14 | API 키 주입 | `application-local.yaml` + `.gitignore`, `--spring.profiles.active=local` |
| 15 | 익명 접근 | 익명에 홈/상세/검색 공개, 작성/수정/삭제만 인증 |
| 16 | 검색 범위 | 레시피 이름만 — `LIKE '%keyword%'` |
| 17 | 이미지 개수 (Task 7) | 썸네일 1장만 (`Recipe.imageUrl`) |
| 18 | 이미지 저장 (Task 7) | 로컬 `./uploads/` + Spring static-locations |
| 19 | 댓글 구조 (Task 7) | 1단계 대댓글 (self-FK `parent`, 손주 X) |
| 20 | 댓글 액션 (Task 7) | 삭제만 (작성자 + ADMIN), 수정 X |
| 21 | 좋아요 표시 | 카운트만 (누가 좋아했나 목록 X) |
| 22 | 좋아요 중복 방지 | `(recipe_id, user_id)` unique + toggle |
| 23 | 댓글 정렬 | 부모 createdAt ASC, 자식도 createdAt ASC |
| 24 | UI 방식 (Task 7) | JS 없음, 모두 폼 POST + redirect |
| 25 | 이미지 검증 | jpg/jpeg/png/webp 화이트리스트, 5MB 제한 |
| 26 | 배포 환경 | AWS EC2 (사용자가 AWS만 알기 때문) — 단, 다른 프로젝트 끝나면 |
| 27 | 수익 모델 (Stage A-3) | 쿠팡 파트너스 우선 — 기존 Naver 연동과 자연 fit |
| 28 | 비밀번호 정책 (T1-3) | 최소 8자 + 영문/숫자 필수 (`@Pattern`) |
| 29 | 잠금 방식 (T1-3) | 메모리 카운터 (`ConcurrentHashMap`) — 5회/15분, DB 컬럼 X |
| 30 | 잠금 해제 (T1-3) | 자동 — 15분 경과 시 카운터 만료, 관리자 개입 불필요 |

---

# Verification

## Task 3 — 완료
- ✅ admin 시드: 첫 부팅 후 `users`에 `username='admin', role='ADMIN'` 한 행. 재부팅 시 그대로
- ✅ admin 로그인: `admin / admin123!!` (Stage A-1 이전 시점). 현재는 env/랜덤 비번
- ✅ 권한 차단: USER가 `/admin/ingredients` 접근 시 403
- ✅ Mock / Real Naver 동작
- ✅ 카테고리 수정 / 사용자 조회 필터 / TTL 자동 refetch / 카테고리 보존
- ✅ UnitParser 단위 테스트 (`UnitParserTest.java`)

## Task 4·5 — 완료
- ✅ 회원가입 → 로그인 → 레시피 생성 → 재료 추가 → 원가 조회
- ✅ 비로그인 `/recipes` → 로그인 리다이렉트
- ✅ 사용자 A의 레시피를 B가 수정/삭제 불가 (403)

## Task 6 — 완료
- ✅ 익명 → 홈 → 검색 → 상세 보기
- ✅ 익명 → `/recipes/{id}/edit` 직접 → 로그인 리다이렉트
- ✅ A 작성 → B는 상세 보임, edit 직접 접근 시 403 (커스텀 페이지)
- ✅ admin 재료 fetch → 카테고리 부여 → 원가 정상 계산
- ✅ `/recipes/99999` → 404 커스텀 페이지

## Task 7 — 완료
- ✅ 이미지 1장 첨부 후 detail에 표시
- ✅ 5MB 초과 / 잘못된 확장자 거부
- ✅ 편집 시 교체 / 기존 이미지 삭제 동작
- ✅ 익명은 좋아요 수만 보임, 인증 사용자는 toggle
- ✅ 좋아요 중복 방지 (unique 제약)
- ✅ 루트 댓글 + 1단계 대댓글 작성/삭제, 대댓글에 답글 거부
- ✅ B가 A의 댓글 삭제 시도 → 403, ADMIN은 가능
- ✅ uploads/ 디렉토리 .gitignore 처리

## Stage A — 완료 (배포 외)
- ✅ admin 비번 env 우선 + 랜덤 fallback (부팅 로그 노출 1회)
- ✅ Dockerfile 빌드 성공 (`docker build -t coast-calculator:test .`)
- ✅ docker-compose.prod.yml 작성
- ✅ 쿠팡 검색 어필리에이트 코드 + 환경변수 토글
- ✅ deployment.md 작성
- ⏸ EC2 실제 배포는 보류 (사용자 결정)

## T1-1 Flyway — 완료
- ✅ `flyway_schema_history` 생성, V1이 BASELINE 타입
- ✅ `ddl-auto: validate` 통과 (Hibernate ↔ DB 일치)
- ✅ 부팅 7.75s 정상
- ✅ 기존 6개 테이블 + 데이터 보존

## T1-3 비밀번호 정책 + brute force — 완료
- ✅ 컴파일 + 부팅 정상 (`LoginAttemptService` 빈 주입, 에러 없음)
- ✅ 비번 정책 거부 케이스:
  - `pass` (4자) → "8자 이상" 에러
  - `password` (영문만) → "영문과 숫자" 에러
  - `12345678` (숫자만) → "영문과 숫자" 에러
- ✅ 비번 정책 통과: `pass1234` (8자 영숫자) → 가입 성공
- ✅ 5회 연속 잘못된 비번 → 6번째 시도부터 `/login?locked` 리다이렉트 ("15분 후" 메시지)
- ✅ 잠금 상태에서 정확한 비번 시도 → 여전히 잠금 (잠금이 우선)
- ✅ 15분 경과 후 정확한 비번 → 로그인 성공 (자동 해제)
- ✅ 성공 시 카운터 리셋 (`AuthenticationSuccessEvent` 구독)

---

# 다음 단계

## T1-6 핵심 통합 테스트 3종 ← 다음

`improvements.md` T1-6.

목표 (최소 안전망):
- `@WebMvcTest`로 SecurityConfig 권한 룰 검증 (관리자/일반/익명 경로 분기)
- `@DataJpaTest`로 EntityGraph 동작 검증 (LazyInit 방어)
- `RecipeService.findMine` 소유자 체크, `IngredientService.fetchAndUpsert` 카테고리 보존 등 핵심 분기 단위 테스트

세부 설계는 작업 시작 시 결정.

## 이후 후보 (improvements.md 참조)

- T2-9 외부 호출(Naver API) 타임아웃 + 리트라이
- T2-13 Actuator + 모니터링
- T1-4 시크릿 외부 저장소 (현재 application-local.yaml 분리만 — 운영급 Vault/AWS Secrets 미적용)
- (보류) EC2 실제 배포
