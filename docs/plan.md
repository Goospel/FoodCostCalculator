# coastCalculator — 작업 계획 / 진행 상태

안정적인 아키텍처·도메인·규칙은 [CLAUDE.md](../CLAUDE.md) 참조. 트러블슈팅 기록은 [troubleshooting.md](troubleshooting.md), 배포 readiness 백로그는 [improvements.md](improvements.md), 수익화 결정 근거/철학은 [monetization.md](monetization.md). 이 문서는 **Task 진행 상태, Task별 상세, Open Questions, Verification 체크리스트, 수익화 진행 추적** 등 진행하면서 갱신되는 내용만 담음.

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
| T1-6 | 핵심 통합 테스트 4종 (H2+Mockito+@DataJpaTest+@SpringBootTest, 56 테스트) | ✅ |
| T2-9 | Naver API 타임아웃(5s/10s) + Spring Retry(3회 지수 backoff) + Recover fallback | ✅ |
| T3-22 후속 | GitHub Actions CI/CD (`.github/workflows/ci.yml`) — test + GHCR 이미지 푸시 | ✅ |
| T3-17 | selectedIngredient UI — "이 제품으로 고정" 드롭다운 + 카테고리/단위 검증 (64 테스트) | ✅ |
| T2-7 | 페이지네이션 — 홈/검색/내 레시피, 페이지 크기 12, prev/next + ±2 페이지 번호 (65 테스트) | ✅ |
| T3-18 | 카테고리 마스터 — `categories` 테이블 + Flyway V2 + datalist 자동완성 (73 테스트) | ✅ |
| T2-11 | N+1 점검 후속 — two-step 쿼리(ID Page → IN 절 + EntityGraph) + findMine 강제 초기화 제거 (76 테스트) | ✅ |
| T3-18.2 | 카테고리 alias/synonym — `category_aliases` + Flyway V3 + 매칭 단계 정규화 + admin UI (95 테스트) | ✅ |
| T2-8 | 비동기/스케줄러 Naver refetch — viewByCategory 블로킹 제거 + @Async 트리거 + @Scheduled 1h (107 테스트) | ✅ |
| T1-4 | 시크릿 외부화 정책 강화 — 운영 프로파일 + ProductionSecretsValidator + 운영 가이드 (119 테스트) | ✅ |
| T3-19 | 가격 이력 — Flyway V4 + `ingredient_price_history` + 변동 시 적재 + admin UI (123 테스트) | ✅ |
| T2-12 | Optimistic locking — Recipe `@Version` + Flyway V5 + 409 충돌 페이지 (126 테스트) | ✅ |
| T2-10 | Caffeine 캐싱 — categoryNames / categoryAliasMap / ingredientGroupsVisible + @CacheEvict (134 테스트) | ✅ |
| **다음** | **(보류) T2-13 Actuator + 모니터링 — 배포 직전 일괄 처리** | ⏸ |

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
| 31 | selectedIngredient UI (T3-17) | 폼 GET 시점에 `findAllVisible` 전체를 카테고리별 `<optgroup>`으로 렌더링, JS 없음 |
| 32 | selectedIngredient 카테고리/단위 불일치 (T3-17) | 서버에서 에러 던지고 저장 거부 (덮어쓰기·무시 X) |
| 33 | 수익화 모델 큰 방향 | 단기: 광고(AdSense) + 제휴(쿠팡 → 네이버/11번가 확장). 중기: 프리미엄/데이터. 장기 후보: B2B 외식업 SaaS 분리/Pivot 재검토 (6-12개월). 세부는 [docs/monetization.md](monetization.md) |

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

## T1-6 핵심 통합 테스트 4종 — 완료
- ✅ 테스트 인프라: H2 (MySQL 호환) + `application-test.yaml` (Flyway off, ddl-auto:create-drop, Naver Mock 강제, `./build/test-uploads`)
- ✅ `LoginAttemptServiceTest` (13) — `Clock` 주입 + `MutableClock`으로 시간 진행 시뮬레이션
- ✅ `RecipeRepositoryTest` (4, `@DataJpaTest`) — EntityGraph 4개 메서드 + Hibernate Statistics 쿼리 카운트로 N+1 회귀 방지
- ✅ `RecipeServiceTest` (6) — `findMine` 소유자/미존재/타인 분기, delete 간접 보호
- ✅ `IngredientServiceTest` (5) — fetchAndUpsert 카테고리 보존(invariant), 신규 insert null, 파싱 실패 skip, blank 키워드 가드
- ✅ `SecurityConfigTest` (16, `@SpringBootTest`+MockMvc) — 익명/USER/ADMIN × 공개/인증/관리자 경로 권한 룰
- ✅ **전체 56 테스트, ./gradlew test 24초 통과**
- ✅ `LoginAttemptService`에 `Clock clock` 필드 + 테스트 생성자 추가 (운영 코드 작은 리팩토링 — 기본 생성자 유지)
- ✅ Spring Boot 4 패키지 분리 대응: `@DataJpaTest` → `org.springframework.boot.data.jpa.test.autoconfigure`, `TestEntityManager` → `org.springframework.boot.jpa.test.autoconfigure`, `@AutoConfigureMockMvc` → `org.springframework.boot.webmvc.test.autoconfigure`

## T3-17 selectedIngredient UI — 완료
- ✅ `RecipeIngredientForm.selectedIngredientId` 추가 + `from()` 갱신
- ✅ `RecipeService` IngredientRepository 주입 + `resolveSelectedIngredient` 검증 헬퍼 (미존재/카테고리 null/카테고리 불일치/단위 불일치 모두 IllegalArgumentException)
- ✅ `RecipeController` IngredientService 주입 + `loadIngredientGroups()` (LinkedHashMap 그루핑) + GET /new, GET /{id}/edit, 검증 실패 시 모두 모델에 ingredientGroups 추가
- ✅ `form.html` 5번째 칼럼 "고정 제품 (선택)" + `<optgroup>` 그루핑된 ingredient option
- ✅ `detail.html` 재료 테이블 5번째 칼럼에 selectedIngredient.title 또는 `— (자동)`
- ✅ `RecipeRepository.findWithDetailsById` EntityGraph에 `ingredients.selectedIngredient` 추가 (open-in-view: false 대응)
- ✅ RecipeServiceTest +5 케이스 — set / 단위 불일치 / 카테고리 불일치 / null 유지 + ingredientRepository 미호출 / 미존재 ID
- ✅ **전체 64 테스트 통과 (이전 59 → +5)**

## T2-9 Naver API 안정성 — 완료
- ✅ 의존성: `spring-retry:2.0.12` (Boot BOM 미관리 → 버전 명시) + `spring-aspects` (BOM 관리)
- ✅ `RetryConfig` (`@EnableRetry`)
- ✅ `RealNaverShoppingClient` 빌더: `JdkClientHttpRequestFactory` + connect 5s / read 10s
- ✅ `@Retryable`: `ResourceAccessException`, `HttpServerErrorException` → 3회, 1s→2s→4s 지수 backoff. 4xx는 재시도 X
- ✅ `@Recover`: 빈 리스트 + WARN 로그 — 사용자 UX 보호
- ✅ `NaverApiProperties`에 `connectTimeoutMs`, `readTimeoutMs`, `maxAttempts`, `initialBackoffMs` + 기본값 보정
- ✅ application.yaml + application-test.yaml에 설정 노출
- ✅ 통합 테스트(`RealNaverShoppingClientRetryTest`, 3): JDK 내장 HttpServer로 stub Naver 운영
  - 5xx 2회 → 3회째 성공 → 정확히 3번 호출, 결과 반환
  - 5xx 영속 실패 → 3회 호출 후 Recover 빈 리스트
  - blank 키워드 즉시 빈 리스트 (네트워크 호출 X)
- ✅ **전체 59 테스트 29초 통과**

---

# T2-12: Optimistic Locking (@Version) — 완료 (2026-05-25)

`improvements.md` T2-12 해결.

## 문제 정리
- 사용자 A와 B(또는 같은 사용자 두 탭)가 같은 레시피를 동시에 편집 → 나중에 저장한 쪽이 먼저 저장 덮어씀 (lost update)
- 사용자 데이터 손실 + "분명 저장했는데 사라졌다" 신뢰 손상

## 해결 — @Version optimistic locking

### 데이터
- **`V5__recipe_version.sql`**: `recipes.version BIGINT NOT NULL DEFAULT 0`. 기존 row는 0으로 시작
- **`Recipe.version`** `@Version` 필드 — Hibernate가 UPDATE 시 `WHERE id=? AND version=?` 조건 추가, 매번 +1

### 충돌 처리 흐름
1. 두 트랜잭션이 같은 version(예: v=0)으로 Recipe 로드
2. 트랜잭션 A 먼저 commit → DB v=1
3. 트랜잭션 B commit 시도 → `WHERE version=0` 매치 안 됨 → 0 rows updated
4. Hibernate `OptimisticLockException` → Spring data 변환 → `ObjectOptimisticLockingFailureException`
5. `GlobalExceptionHandler.handleOptimisticLockingFailure` → 409 Conflict + `error/conflict.html` 렌더

### UX (`error/conflict.html`)
- 친화적 안내: "다른 곳에서 먼저 수정되었습니다"
- **데이터 손실 방지 가이드**: 브라우저 뒤로가기 → 입력 내용 메모 → 수정 페이지 새로 → 최신 상태 확인 → 합치기 → 다시 저장
- 홈/내 레시피 목록 버튼

### 스코프 결정
- **Recipe만 @Version** — 가장 충돌 빈도 높은 엔티티(사용자 본인 두 탭 편집)
- **RecipeIngredient는 부여 X** — Recipe @Version cascade UPDATE로 보호 (RecipeService.update가 clearIngredients + 재추가 패턴)
- **Ingredient는 부여 X** — admin만 카테고리 단일 필드 만지고 충돌 가능성 낮음
- **Comment는 부여 X** — 수정 기능 없음(작성/삭제만), 충돌 시나리오 없음

## 의도적 비포함 → 후속
- **자동 머지**: 같은 필드 다르게 수정 시 사용자 의도 모호. 거부 후 사용자가 직접 합치는 게 안전
- **현재 DB 상태를 충돌 페이지에 보여주기**: 추가 정보 + 폼 데이터 보존이 더 복잡. 첫 안전망은 단순 안내 + 뒤로가기 가이드
- **Ingredient/Comment @Version**: 위 이유로 의도적 비포함

## 테스트 (총 126, 이전 T3-19 123 → +3)
- `RecipeRepositoryTest` +3:
  - 신규 persist 시 `version=0` 초기화 검증
  - 수정 후 flush 시 `version +1` 증가 검증
  - **동시 수정 시뮬레이션** — stale 복사본(detach) + fresh 영속(수정+flush 후 v=1) → stale의 `saveAndFlush` 시 `ObjectOptimisticLockingFailureException` 검증 (Spring repository 거쳐 변환된 예외)

---

# T3-19: 가격 이력 (IngredientPriceHistory) — 완료 (2026-05-25)

`improvements.md` T3-19 해결. 수익화 단계 3(Freemium 차트) + 단계 4(데이터 라이선싱)의 공통 선행. **누적 시간이 필요한 항목**이라 일찍 착수해서 데이터 모으기.

## 정책 결정

사용자 선택 (2026-05-25):
- **적재 정책 B**: 가격 변동(pricePerGram BigDecimal.compareTo != 0) 시에만 적재. 신규 ingredient는 첫 fetch 시 무조건 적재 (시작 시점)
- **노출 X**: admin만(`/admin/ingredients/{id}/history`). 일반 사용자 공개는 수익화 단계 3 진입 시 무료/유료 라인 결정 후

옵션 A(모든 fetch 적재) 거부 — 데이터 중복 + 매시간 스케줄러로 인한 누적 부담.
옵션 C(변동 + 하루 1회 강제) 거부 — 절충안의 복잡도 ↑.

## 변경

### 데이터
- **`V4__ingredient_price_history.sql`** — `ingredient_price_history(id, ingredient_id FK CASCADE, naver_product_id snapshot, price, total_amount, unit, price_per_gram, recorded_at)` + 인덱스 `(ingredient_id, recorded_at DESC)`. 기존 ingredients 백필 (시작 시점 = ingredients.fetched_at).

### 도메인
- **`IngredientPriceHistory`** 엔티티: `@ManyToOne(LAZY)` ingredient, `naverProductId` snapshot(감사용), 가격/용량/단위/단가/recordedAt. 정적 팩토리 `snapshotOf(Ingredient, recordedAt)`.
- **`IngredientPriceHistoryRepository`**: `findByIngredientIdOrderByRecordedAtDesc(id)` + `findByIngredientIdAndRecordedAtBetweenOrderByRecordedAtAsc(id, from, to)` (후속 차트/리포트).

### 통합 (IngredientService.fetchAndUpsert)
- 기존 ingredient: 갱신 직전 `pricePerGram` 백업 → `refreshFromNaver` 후 비교 (`compareTo != 0`) → 다르면 `priceHistoryRepository.save(snapshotOf(...))`
- 신규 ingredient: `save` 후 무조건 history 적재 (시작 시점)
- pricePerGram 비교라 **price 같지만 totalAmount 변동** 케이스(예: 1kg → 1.5kg)도 적재됨

### Admin UI
- **`GET /admin/ingredients/{id}/history`**: 표 형식 (최신순). 이전 행과 비교한 단가 차이(`+` 빨강 / `-` 초록 / `— (최초)` 회색) 표시.
- `admin/ingredients/list.html`에 "이력" 링크 추가.

## 의도적 비포함 → 후속
- **일반 사용자 공개**: 수익화 단계 3(Freemium) 도입 시 무료/유료 라인 결정 후. 현재는 admin만.
- **차트 시각화**: 표만 제공. JS 없음 정책상 향후 SVG 정적 생성 또는 별도 라이브러리 도입 PR.
- **기간 필터 UI**: 기본은 전체. 데이터 누적 후 1주/1개월/1년 필터 추가 가능.
- **카테고리별 평균 가격 추이**: 단일 ingredient만. 카테고리 단위 통계는 후속(데이터 라이선싱과 연결).
- **데이터 보존 정책 / archival**: 무한 누적. 1년+ 누적 시 partition 또는 cold storage 검토.

## 테스트 (총 123, 이전 T1-4 119 → +4)
- `IngredientServiceTest` +4:
  - 신규 ingredient는 무조건 history 적재
  - 기존 + price 변동 → 적재
  - 기존 + 동일 price → 적재 X (옵션 B 검증)
  - price 같지만 totalAmount 다름 → pricePerGram 변동이라 적재 (BigDecimal.compareTo 동작 검증)

---

# T1-4: 시크릿 외부화 정책 강화 — 완료 (2026-05-25)

`improvements.md` T1-4 해결 (옵션 A — 환경변수 정책 강화 + 운영 가이드. AWS SM/Vault 실제 통합은 의도적 보류).

## 정책 결정
사용자가 옵션 A 선택 (2026-05-25):
- 코드 변경 최소, 즉시 적용 가능
- 부팅 시 필수 시크릿 검증으로 안전망
- 외부 저장소 통합은 운영 인스턴스 수 ≥ 2 또는 시크릿 종류 ≥ 10 즈음 재검토
- AWS SM 통합 hook은 deployment.md에 문서화만 (실 도입 X)

## 변경

### 운영 프로파일
- **`application-prod.yaml`** 신규 — `SPRING_PROFILES_ACTIVE=prod`로 활성:
  - `spring.jpa.show-sql: false`, `format_sql: false` (운영 SQL 노출 최소화)
  - `spring.thymeleaf.cache: true` (운영 템플릿 캐시)
  - `naver.api.mock-enabled: ${NAVER_MOCK_ENABLED:false}` (운영 기본 실 API)
- **`docker-compose.prod.yml`** 갱신 — `SPRING_PROFILES_ACTIVE: prod` 자동 주입

### 검증기
- **`ProductionSecretsValidator`** (`@Profile("prod")` + `@PostConstruct`):
  - DB 자격증명이 디폴트(`coast`/`coastpass`) 그대로 → 거부
  - `INITIAL_ADMIN_PASSWORD` 비어있음 → 거부 (운영에서 랜덤 비번 로그 노출은 회전 어려움 + 로그 유출 시 즉시 침해)
  - `naver.api.mock-enabled=false`인데 `NAVER_CLIENT_ID`/`SECRET` 비어있음 → 거부
  - 위반 1개 이상 시 모두 모아 `IllegalStateException` (사용자가 환경변수 몇 개 빠졌는지 한 번에 파악)
  - 통과 시 INFO 로그 한 줄

### 시크릿 템플릿
- **`.env.prod.example`** 갱신:
  - `[REQUIRED]` 마커 명확화
  - `__REPLACE_WITH_OPENSSL_RAND_24__` placeholder (디폴트 그대로 두면 부팅 거부)
  - `NAVER_MOCK_ENABLED=false` 기본
  - 안내 보강: `openssl rand -base64 24` 사용법

### 운영 가이드 (`deployment.md` § 9)
- § 9-1 `.env.prod` 파일 권한 (chmod 600, ownership 명시)
- § 9-2 systemd `EnvironmentFile` 패턴 (Docker 미사용 시)
- § 9-3 GitHub Actions Secrets 관리 (운영 시크릿은 CI에 노출 X)
- § 9-4 시크릿 회전 절차 표 (admin/DB/Naver/Coupang 주기 + 절차)
- § 9-5 외부 저장소 통합 hook 문서화 — AWS SM/Vault/SOPS 옵션 + 도입 트리거 ("인스턴스 ≥ 2 또는 시크릿 ≥ 10")

## 의도적 비포함 → 후속
- **AWS Secrets Manager 실제 의존성 추가**: 실 AWS 계정 + IAM Role 필요. 현 단계 과대투자라 문서화만.
- **HashiCorp Vault 셀프 호스팅**: 운영 복잡도 ↑ — Vault 서버 자체 관리 부담.
- **SOPS/git-crypt 암호화된 시크릿 파일**: `.env.prod`는 EC2 위에서만 다루는 정책이라 git에 암호화 저장 불필요.
- **시크릿 회전 자동화**: 현재 수동 절차. 분기 1회 정도 빈도라 자동화 ROI 낮음.

## 테스트 (총 119, 이전 T2-8 107 → +12)
- `ProductionSecretsValidatorTest` 신규 12:
  - HappyPath 2 (mock=true/false 둘 다 통과)
  - DbCredentials 2 (디폴트 그대로 거부 / username만 디폴트는 통과)
  - AdminPassword 3 (빈 문자열/공백만/null)
  - NaverCredentials 4 (mock=true ignore / client-id만 빈 / secret만 빈 / 둘 다 빈 → 두 위반 동시 보고)
  - `validate()` 통합 1 — 세 항목 모두 위반 시 모두 모아 IllegalStateException

---

# T2-8: 비동기/스케줄러 기반 Naver refetch — 완료 (2026-05-25)

`improvements.md` T2-8 해결. T2-9 (외부 호출 안정성)와 짝.

## 문제 정리
기존 `IngredientService.viewByCategory(category)`:
- 캐시 조회 후 stale(빈 결과 또는 fetched_at 24h 전)이면 **사용자 요청 스레드에서** `fetchAndUpsert` 호출 → Naver 응답까지 대기
- T2-9로 timeout(5s+10s) + Retry(3회 지수 backoff) 적용했지만, 최악의 경우 **사용자 응답 15s+ 지연** 가능
- Naver 장애 시 톰캣 스레드 점진적 고갈 위험

## 해결 — 두 갈래 접근

**1. 사용자 요청은 절대 블로킹 X (viewByCategory)**:
- 캐시(stale 허용) 즉시 반환
- stale 감지 시 `IngredientRefetchService.triggerAsyncRefetch(category)` 백그라운드 트리거만
- `@Transactional(readOnly = true)`로 변경 — 외부 호출 안 함

**2. 백그라운드 자동 갱신 (Scheduled)**:
- `IngredientRefetchService.scheduledRefresh()` `@Scheduled(fixedDelay=1h)`
- `IngredientRepository.findDistinctStaleCategoriesBefore(threshold)`로 stale 카테고리 일괄 추출 → 각각 trigger

## 변경

### 인프라
- **`AsyncConfig`** 신규: `@EnableAsync` + `@EnableScheduling` + `ThreadPoolTaskExecutor` 빈 `"naverRefetchExecutor"` (core 2 / max 4 / queue 100 / `DiscardPolicy` — 큐 차면 즉시 빠짐).
- **application.yaml**: `naver.api.scheduled-refresh-{enabled,interval-ms,initial-delay-ms}` 추가 (기본 enabled / 1h / 1분).
- **application-test.yaml**: `scheduled-refresh-enabled=false` — 테스트 격리.

### 도메인
- **`IngredientRefetchService`** 신규:
  - `triggerAsyncRefetch(category)` `@Async("naverRefetchExecutor")` — `ConcurrentHashMap<String, AtomicBoolean>` 카테고리별 락. 이미 진행 중이면 skip, finally로 항상 해제(예외 시에도). null/blank no-op.
  - `scheduledRefresh()` `@Scheduled(fixedDelayString="${...:3600000}", initialDelayString="${...:60000}")` — stale category 일괄 trigger. 비활성 플래그(`@Value`로 주입한 `scheduledRefreshEnabled`)면 즉시 return.
- **`IngredientService`**: `viewByCategory`는 `@Transactional(readOnly=true)`로 변경. 블로킹 `fetchAndUpsert` 호출 제거 → `refetchService.triggerAsyncRefetch(category)`만 트리거 후 캐시 즉시 반환.
  - 생성자에 `@Lazy IngredientRefetchService` 주입 — 양방향 의존(서비스 → refetch → fetchAndUpsert) 부팅 초기화 순서 보호 (`@RequiredArgsConstructor` 제거하고 명시 생성자).
- **`IngredientRepository`**: `findDistinctStaleCategoriesBefore(LocalDateTime threshold)` JPQL 쿼리 추가 — category IS NULL 제외 + fetched_at < threshold.

## 의도적 비포함 → 후속
- **ShedLock (분산 락)**: 멀티 인스턴스 배포 시 `@Scheduled` 중복 실행 방지 필요. 현재 EC2 단일 머신 가정이라 미적용. K8s 전환 시 필수.
- **카테고리별 우선순위 / Refetch 큐 영속화**: 모든 stale 카테고리를 한 사이클에 trigger — 카테고리 수십 개 넘으면 우선순위 정책 필요해질 수 있음.
- **빈 결과 사용자 안내 UI**: 처음 카테고리 진입 시 빈 페이지 → "잠시 후 새로고침" 안내 추가는 별도 UI 폴리시.
- **부팅 직후 초기 prime**: initialDelay 1분 동안은 stale 갱신 안 됨. 부팅 직후 사용자가 stale을 보면 트리거되니 실용상 충분.

## 테스트 (총 107, 이전 T3-18.2 95 → +12)
- `IngredientRefetchServiceTest` 신규 8:
  - triggerAsyncRefetch 정상 호출 + 락 해제 / blank no-op / 동시 같은 카테고리 락 / 다른 카테고리 독립 / 예외 시 락 해제
  - scheduledRefresh disabled=false 즉시 return / stale each trigger / stale 없으면 no-op
- `IngredientServiceTest` 7 → 11 (+4):
  - viewByCategory stale 캐시 → 비동기 trigger만, 블로킹 fetch X
  - fresh 캐시 → trigger 호출 X
  - 빈 캐시 → 빈 리스트 즉시 + trigger
  - null/blank → 즉시 빈 리스트 + 어떤 호출도 안 함

---

# T3-18.2: 카테고리 alias/synonym — 완료 (2026-05-25)

`improvements.md` T3-18 비고 — alias 부분도 해결.

## 정책 결정 (옵션 A — 매칭 단계만 정규화)

사용자 입력은 그대로 저장(예: `RecipeIngredient.categoryName = "박력분"`), ingredient 매칭/검증 단계에서만 alias를 풀어 canonical("밀가루")로 검색. **T3-17의 "사용자 의도 보존, 자동 덮어쓰기 X" 정책과 일관**. 반대 옵션 B(저장 시 정규화)는 사용자 의도 손실 우려로 거부.

## 변경

### 데이터 모델
- **`V3__category_aliases.sql`**: `category_aliases(id, alias unique, canonical_category_id FK→categories ON DELETE CASCADE, created_at)`. 표준 SQL (MySQL/H2 호환).
- **`CategoryAlias`** 엔티티: `alias` unique + `canonical` ManyToOne LAZY + `created_at` CreationTimestamp.
- **`CategoryAliasRepository`**: `findByAlias`, `existsByAlias`, `findAllByOrderByAliasAsc` (`@EntityGraph(canonical)` — admin 리스트 LazyInit 방지).

### 핵심 서비스
- **`CategoryAliasService`**:
  - `resolve(input)` — 우선순위: (1) categories.name에 있으면 input 그대로(canonical 우선), (2) aliases에 있으면 canonical.name 반환, (3) 둘 다 없으면 input 그대로(자유 입력 보존). null/blank 그대로.
  - `add(alias, canonical)` — 5가지 검증: blank 거부 / 자기 자신 거부 / alias가 이미 canonical 이름이면 거부 / alias 중복 거부 / canonical 미존재 거부.
  - `delete(id)` — 미존재 거부.

### 통합 지점
- **`RecipeCostCalculator.calculate`**: `findByCategoryAndUnit` 직전에 `aliasService.resolve(ri.getCategoryName())` 적용. selectedIngredient 분기는 그대로(alias 풀이 미적용 — 명시 선택의 우선순위 유지).
- **`RecipeService.resolveSelectedIngredient`** (T3-17 검증): row category와 selected.category 양쪽 resolve 후 `equalsIgnoreCase` 비교. 한 쪽이 alias여도 풀어서 같으면 통과.

### Admin UI
- **`/admin/category-aliases`**: 목록 + 추가 폼(한 페이지). canonical 선택은 HTML5 `<datalist>` 자동완성 — **JS 없음 정책 유지**. 추가/삭제는 폼 POST + redirect. `admin/ingredients/list.html`에 진입 버튼 추가.
- 보안: `/admin/**` ROLE_ADMIN 룰로 자동 보호 (SecurityConfig 변경 X).

## 의도적 비포함 → 후속
- **저장 시 정규화**: 옵션 B 자체를 거부 (사용자 의도 보존 정책).
- **alias의 alias (체인)**: 의도적으로 X. canonical은 alias 될 수 없음(검증 #3) + alias의 canonical FK는 Category만 가리킴 → 체인 자체가 만들어질 수 없는 구조.
- **bulk import**: alias 다수 등록 시 폼으로만 가능. CSV/API는 후속.
- **alias 검색 노출**: 사용자 폼 datalist에는 canonical만 노출. alias는 사용자가 직접 입력하면 풀려서 매칭됨.

## 테스트 (총 95, 이전 T2-11 76 → +19)
- `CategoryAliasServiceTest` 신규 14:
  - resolve 6 (canonical 우선 / alias 풀이 / unknown 그대로 / trim / null / blank)
  - add 7 (정상 / blank alias / blank canonical / 자기자신 / canonical 중복 / alias 중복 / canonical 미존재)
  - delete 1 (미존재 ID)
- `RecipeCostCalculatorTest` 신규 3: alias 풀이 매칭 OK / 풀이 후에도 미매칭 / selectedIngredient는 alias 경로 우회
- `RecipeServiceTest` +2: alias 풀이로 카테고리 일치 인정 / 풀이 후에도 다른 canonical은 거부 (총 11→13)

---

# T3-18: 카테고리 마스터 — 완료 (2026-05-25)

`improvements.md` T3-18 부분 해결 (마스터 + 자동완성). alias/synonym은 별도 후속(T3-18.2 또는 새 항목).

## 설계 결정
- **점진적 안전 접근(옵션 A)**: `Ingredient.category`는 String 그대로 유지 (FK 전환 X). `Category` 마스터 테이블은 별도 — admin/사용자 폼의 `<datalist>` 자동완성 권장 목록 용도.
- FK 전환을 안 한 이유:
  - 마이그레이션 부담↓
  - `RecipeIngredient.categoryName`이 자유 입력이라 일관성 문제 동일하게 남음 (그 측은 사용자 검색 키워드 의도)
  - T3-17 검증 로직(`equalsIgnoreCase`)과 자연 결합
- alias/synonym(박력분 → 밀가루) 매핑 미포함 — 별도 후속

## 변경
- **Flyway `V2__category_master.sql`**: `categories(id, name unique, created_at)` 테이블 생성 + 기존 `ingredients.category` distinct 값 INSERT (NOT IN 패턴, MySQL/H2 표준 SQL).
- **`domain/category/`** 신규: `Category` 엔티티 / `CategoryRepository`(findByName, existsByName, findAllByOrderByNameAsc) / `CategoryService`(findAllNames, ensureExists 멱등).
- **`IngredientService.updateCategory`**: 카테고리 저장 시 `categoryService.ensureExists(name)` 호출 → 마스터 자동 등록. null/blank는 skip.
- **`AdminIngredientController.editForm` + `admin/ingredients/edit.html`**: categoryNames 모델 주입 + `<datalist id="categoryOptions">` 자동완성.
- **`RecipeController` GET 폼 경로 4곳 + `recipes/form.html`**: categoryNames 모델 주입 + 모든 재료 행이 공유하는 `<datalist id="recipeCategoryOptions">` (DOM 한 번만 정의).
- HTML5 `<datalist>`만 사용 — **JS 없음 정책 준수**. 입력 자유도 유지하면서 기존 카테고리 노출.

## 의도적 비포함 → 후속 항목
- **alias/synonym 매핑** (박력분 → 밀가루 같은 유의어): 별도 항목으로 plan에 추가.
- **카테고리 삭제 UI**: Ingredient.category가 String이라 마스터 삭제해도 데이터 남아 사용자 혼란만 유발. 정리는 SQL 수동 또는 향후 admin 페이지.
- **`Ingredient.category` FK 전환**: 위 이유로 안 함. 필요 시 별도 마이그레이션 PR.

## 테스트 (총 73, 이전 65 → +8)
- `CategoryServiceTest` 신규 6 — ensureExists(신규 insert/이미 존재 skip/trim/null no-op/blank no-op) + findAllNames 매핑
- `IngredientServiceTest` +2 — updateCategory가 ensureExists를 호출(새 카테고리) / null이면 호출 X

---

# T2-11: N+1 점검 후속 — 완료 (2026-05-25)

`improvements.md` T2-11 해결. T2-7에서 의도적으로 미뤘던 "ToMany + Pageable in-memory paging" root cause 정리 + `findMine`의 `.getIngredients().size()` 강제 초기화 패턴 제거 + troubleshooting [TS-3](troubleshooting.md) 잔존 위험 해소.

## 문제 정리

**증상 1**: ToMany 컬렉션(`ingredients`)을 EntityGraph로 fetch하면서 Pageable을 적용하면 Hibernate가 `firstResult/maxResults specified with collection fetch; applying in memory` 경고 → 전체 결과를 메모리에 올린 뒤 자름. 페이지 12라 영향 미미하지만 데이터 늘면 부하 잠복.

**증상 2**: `RecipeService.findMine`이 `findById` 후 `recipe.getIngredients().size()` 강제 초기화로 LAZY 회피. 명시적이지 않고 향후 변경에 약함.

## 해결 — two-step 쿼리 패턴

```
1) Page<Long> idPage = repo.findIdsByXxx(pageable)
     → count(*) + SELECT id FROM ... ORDER BY ... LIMIT/OFFSET (collection 미참조, in-memory paging 미발생)
2) List<Recipe> content = repo.findAllWithDetailsByIdInOrderByXxx(idPage.getContent())
     → SELECT entity + LEFT JOIN FETCH (EntityGraph + IN 절 + ORDER BY 보존)
3) return new PageImpl<>(content, pageable, idPage.getTotalElements())
```

`assemblePage(idPage, entityFetcher, pageable)` 헬퍼로 세 페이징 메서드(`findRecent`/`searchByName`/`findMyRecipes`)가 공유.

## 변경
- **`RecipeRepository`**:
  - 추가: ID-only Page 3개 (`findIdsAllByCreatedAtDesc`, `findIdsByNameContainingIgnoreCase...`, `findIdsByUserOrderByUpdatedAtDesc`) — `@Query`로 SELECT r.id 명시
  - 추가: entity fetch 2개 (`findAllWithDetailsByIdInOrderByCreatedAtDesc`, `...OrderByUpdatedAtDesc`) — `@EntityGraph` + IN 절 + ORDER BY
  - 추가: `findWithUserAndIngredientsById` — `findMine` 전용 (`ingredients.selectedIngredient`는 LAZY 유지, 편집 폼에서 ID만 쓰니까)
  - 제거: 기존 `Page<Recipe>` 반환 3개 (T2-7에서 도입했던 메서드)
- **`RecipeService`**:
  - 세 페이징 메서드를 `assemblePage` 헬퍼 + two-step으로 재구현. 빈 idPage면 entity 쿼리 호출 X (불필요한 IN () 회피).
  - `findMine`: `findById` + `.getIngredients().size()` → `findWithUserAndIngredientsById` 한 번 호출로 단순화.
- **`RecipeRepositoryTest`** (5 → 8): 시그니처 변경 따라가기 + 쿼리 카운트 회귀 검증 강화
  - ID-only Page 쿼리는 ≤2 쿼리 (count + select id, collection 미참조)
  - IN 절 + EntityGraph는 ≤2 쿼리 (LEFT JOIN으로 묶임)
  - `findWithUserAndIngredientsById` ≤3 쿼리

## 의도적 비포함 (선택)
- **DataSource Proxy / `use_sql_comments=true`**: 개발 환경 N+1 자동 감지. 별도 PR로 분리 (테스트 카운트 검증으로 회귀는 이미 잡힘).
- **`@SqlResultSetMapping` 또는 projection DTO**: 카드 뷰만 쓸 거면 ingredients 전체 entity가 아니라 `INGREDIENT_COUNT(*)` 같은 카운트만 SELECT하면 더 가벼움. 현재는 `${#lists.size(r.ingredients)}` 가 템플릿에서 직접 호출되어서 entity 필요. 추후 카드 DTO화 시 다룰 항목.

## 테스트 (총 76, 이전 T3-18 73 → +3)
- `RecipeRepositoryTest` 5 → 8 (+3) — `findIdsAllByCreatedAtDesc` 쿼리 카운트, `findWithUserAndIngredientsById` EntityGraph 검증, IN 절 + ORDER BY 보존
- `RecipeServiceTest` 시그니처 갱신만 (`findById` → `findWithUserAndIngredientsById`)

---

# T2-7: 페이지네이션 — 완료 (2026-05-25)

`improvements.md` T2-7 해결.

## 변경
- **`RecipeRepository`** — 세 메서드를 `List<Recipe>` → `Page<Recipe>` 반환으로 전환:
  - `findAllByOrderByCreatedAtDesc(Pageable)`
  - `findByNameContainingIgnoreCaseOrderByCreatedAtDesc(String, Pageable)`
  - `findByUserOrderByUpdatedAtDesc(User, Pageable)` ← 인자 추가
  - EntityGraph는 그대로. Spring Data가 count 쿼리는 별도 발행하니 EntityGraph 영향 없음.
- **`RecipeService`** — `findRecent(int) / searchByName(String, int) / findMyRecipes(String)` → 모두 `Pageable` 인자 + `Page<Recipe>` 반환.
- **`HomeController`** — `?q=&page=0&size=12` 쿼리 파라미터. `size`는 1-50 클램프(비정상 쿼리 방어). 모델 키 `recipesPage`.
- **`RecipeController.list`** — 동일 패턴. `?page=0&size=12`.
- **`home.html` / `recipes/list.html`** — 페이지네이션 UI 추가:
  - `recipesPage.empty` 분기
  - prev/next 버튼 + 현재 ±2 페이지 번호 + 양 끝(0, total-1)
  - 그 사이는 `…` 생략 표시
  - 페이지가 1개뿐이면 `pager` 전체 hide
  - 페이지 메타("전체 N개 · K / total 페이지")
  - `q` 파라미터 보존 (검색 결과 페이지 이동 시 검색어 유지)

## URL 파라미터 정책
- 사용자에게는 1-indexed로 보임 (`number + 1`). 내부/Spring은 0-indexed.
- 페이지 사이즈 기본 12, 상한 50. `size=0` 또는 음수 → 12로 보정.
- 잘못된 `page` (음수) → 0으로 보정.

## 의도적 비포함 (후속)
- **Hibernate `firstResult/maxResults specified with collection fetch; applying in memory` 경고**: ToMany(`ingredients`) + Pageable 조합이라 발생할 수 있음. 페이지 12라 영향 미미. 근본 해결은 **T2-11(N+1 점검)** 에서 two-step 쿼리 또는 ID-only Page → 별도 fetch 패턴으로 처리 예정.
- 내림차순/오름차순 토글, 정렬 기준 변경 UI — 현재 모두 createdAt DESC / updatedAt DESC 고정.
- 무한 스크롤 / htmx 기반 — JS 없음 정책상 prev/next 페이저 유지.

## 테스트 (총 65, 이전 64 → +1)
- `RecipeRepositoryTest` +1 — `findAllByOrderByCreatedAtDesc` 페이지 크기 2 × 5개 데이터로 `totalElements=5, totalPages=3, hasNext/Previous, page2.content=1` 검증
- 기존 3개 테스트(`findAllRecentWithEntityGraph`, `searchByNameIgnoresCase`, `findByUserReturnsOnlyMine`)를 Page 시그니처로 갱신 + totalElements assertion 추가

---

# T3-17: selectedIngredient UI 완성 — 완료 (2026-05-23)

`improvements.md` T3-17 해결.

## 배경
`RecipeIngredient.selectedIngredient` FK는 Task 4 시점부터 있었고 `RecipeCostCalculator`가 `selectedIngredient != null`이면 정책 무시하고 직접 단가 사용하도록 이미 구현되어 있었음. 그러나 폼/DTO/Service가 이 FK를 채울 경로가 없어 **dead code** 상태. T3-17은 그 UI를 채워 넣음.

## 설계 (JS 없음 정책 준수 — Open Q #24)
1. **`RecipeController.GET /recipes/new`, `GET /recipes/{id}/edit`** — `ingredientService.findAllVisible()` (`category != null` 인 것만, 카테고리 ASC + pricePerGram ASC 정렬됨)을 `LinkedHashMap<카테고리, List<Ingredient>>`로 그루핑해 모델에 `ingredientGroups`로 담음.
2. **`form.html`** — 각 재료 행에 다섯 번째 칼럼 "고정 제품 (선택)" 추가. `<select>` 안에 `자동 (정책 사용)` 옵션 + `<optgroup label="카테고리">`별로 ingredient 옵션 (`title — mallName (pricePerGram원/g|ml)`).
3. **`RecipeIngredientForm.selectedIngredientId`** — 새 nullable `Long` 필드. `from(RecipeIngredient)`에서 기존 `selectedIngredient.id`를 채워 편집 폼에서 선택 상태 복원.
4. **`RecipeService.applyIngredients`** — `selectedIngredientId` 있으면 `IngredientRepository.findById`로 조회 후 검증:
   - 미존재 → "선택한 제품을 찾을 수 없습니다 (id=...)"
   - `category == null` → "선택한 제품은 카테고리가 부여되지 않아 사용할 수 없습니다"
   - `category != row.categoryName` (대소문자 무시) → "선택한 제품의 카테고리(...)가 입력 카테고리(...)와 다릅니다"
   - `unit != row.unit` → "선택한 제품의 단위(...)가 입력 단위(...)와 다릅니다"
   모두 `IllegalArgumentException` → GlobalExceptionHandler → 폼으로 다시 렌더 (errorMessage 표시).
5. **`detail.html`** — 재료 테이블에 "고정 제품" 칼럼 추가. `ri.selectedIngredient != null`이면 title, 아니면 `— (자동)`.
6. **`RecipeRepository.findWithDetailsById`** — EntityGraph에 `ingredients.selectedIngredient` 추가. `open-in-view: false` 환경에서 detail.html 렌더링 시 LazyInitializationException 방지. Hibernate가 LEFT JOIN으로 묶어 쿼리 카운트 ≤3 임계값(`RecipeRepositoryTest`) 그대로 통과.

## 의도적 비포함
- **카테고리 자동 변경 / 동기화**: 사용자가 행 카테고리를 "밀가루"로 입력하고 제품을 카테고리 "설탕"인 걸 선택해도 자동으로 행 카테고리를 덮어쓰지 않음. **에러 던지고 저장 거부**하여 사용자 의도(행 카테고리명)를 보존. 사용자가 옵션 1 선택.
- **카테고리 자유 입력 유지**: 폼의 카테고리 input은 여전히 자유 입력 (datalist X). 향후 T3-18 카테고리 정규화에서 다룸.
- **selectedIngredient의 stale 체크**: 사용자가 선택한 시점과 저장 시점 사이에 Naver fetch로 ingredient.title이 바뀔 수 있으나, 단가 자체는 `pricePerGram`만 보면 됨. ID 기반이라 안전.

## 테스트 (총 64, 이전 59 → +5)
- `RecipeServiceTest` 추가 5종 — `selectedIngredientId` 지정 시 set / 단위 불일치 거부 / 카테고리 불일치 거부 / 미지정이면 null + ingredientRepository 호출 X / 존재하지 않는 ID 거부
- `RecipeRepositoryTest` 그대로 통과 — EntityGraph 변경에도 쿼리 카운트 ≤3 유지

---

# T3-22 후속: GitHub Actions CI/CD — 완료 (2026-05-23)

`improvements.md` T3-22 후속 부분 해결.

## 구성
- **`.github/workflows/ci.yml`** — 두 job:
  - **`test`**: PR(→ main) + main push에서 실행. JDK 25 Temurin + Gradle build cache + `./gradlew test --no-daemon --stacktrace`. 실패해도 test report 아티팩트 업로드(`if: always()`). 15분 타임아웃.
  - **`build-and-push`**: main push에서만 실행(`needs: test`). Docker Buildx + GHCR 로그인(`secrets.GITHUB_TOKEN`) + `docker/build-push-action@v6`. `latest` + `sha-<short>` 두 태그 push. GHA 빌드 캐시(`cache-from/to: type=gha`)로 레이어 재사용.
- **권한**: 워크플로우 기본 `contents: read`만, push job에 `packages: write` 추가 (최소 권한).
- **Concurrency**: 같은 ref 새 push 시 이전 잡 취소 — PR push만 (main push는 큐잉).
- **GHCR owner lowercase 처리**: `${{ github.repository_owner }}` (`Goospel`)를 `tr` 로 소문자화 → `ghcr.io/goospel/coastcalculator`. fork/rename 시에도 안전.

## Out of Scope (후속)
- 배포 자동화 (현재는 이미지 빌드/push까지만, EC2 ssh+compose pull은 수동) — Stage C 또는 EC2 실 배포 시.
- PR에 코멘트로 결과 표시(코드 커버리지, Gradle 빌드 스캔 등) — 필요해지면.
- Dependabot/CodeQL — 분리된 워크플로우로 후속.

---

# T2-10: Caffeine 캐싱 — 완료 (2026-05-28)

`improvements.md` T2-10 해결. Tier 2의 마지막 미해결 항목(T2-13 배포 직전 보류 제외) — 단계 1 AdSense 진입 전 부하 안정성 확보.

## 정책 결정
- **Caffeine 단독**: 단일 EC2 인스턴스 가정. Redis 전환은 멀티 인스턴스(≥ 2) 시점으로 보류 — `@Cacheable` 인터페이스 그대로라 type만 바꾸면 됨.
- **읽기 핫패스 3개만 캐싱**: 페이징 메서드(`findRecent`/`searchByName`)는 page/size/q 가변성 ↑ → 캐시 히트율 낮음 + write-through 무효화 비용(`RecipeService.create/update/delete` 마다 allEntries) > 이득. `viewByCategory`는 T2-8 DB 캐시(stale 허용) + async refetch로 충분.
- **30분 통일 TTL**: per-cache 분리는 `CaffeineCacheManager` + 캐시별 빌더 등록이 필요한 과대투자.

## 변경

### 의존성 / 설정
- **`build.gradle`** — `spring-boot-starter-cache` + `com.github.ben-manes.caffeine:caffeine`. TS-1 패턴 — Spring Boot 4 autoconfigure 모듈 분리 대응.
- **`application.yaml`** — `spring.cache.type=caffeine`, `cache-names: [categoryNames, categoryAliasMap, ingredientGroupsVisible]`, `spring.cache.caffeine.spec: maximumSize=2000,expireAfterWrite=30m`.
- **`application-test.yaml`** — `spring.cache.type=none` (기존 테스트 격리, T2-8 `scheduled-refresh-enabled=false` 패턴).

### 코드
- **`config/CacheConfig`** 신규 (`@EnableCaching`).
- **`CategoryService.findAllNames`** — `@Cacheable("categoryNames")`. `ensureExists` — `@CacheEvict(allEntries=true)`.
- **`CategoryAliasService.resolve`** — `@Cacheable("categoryAliasMap", key="#input", condition="#input != null and !#input.isBlank()")`. **condition 사용 이유**: Caffeine은 null key 거부 → `unless`로는 lookup 단계의 null 전달을 못 막음 ([TS-14](troubleshooting.md) 참조). `add`/`delete` — `@CacheEvict(["categoryAliasMap","categoryNames"], allEntries=true)`.
- **`IngredientService.findAllVisible`** — `@Cacheable("ingredientGroupsVisible")`. `updateCategory`/`fetchAndUpsert`/`delete` — `@CacheEvict(allEntries=true)`.

## 의도적 비포함 (후속)
- **페이징 메서드 캐싱**: 위 결정 — 가변성 + 무효화 비용.
- **`viewByCategory` 추가 캐싱**: T2-8 패턴으로 충분.
- **per-cache TTL 분리**: 30분 통일.
- **Redis 전환**: 인스턴스 ≥ 2 시점.
- **캐시 통계/메트릭** (`Caffeine.recordStats()` + actuator endpoint): T2-13(Actuator) 도입 시 같이.
- **per-key evict**: input 표준화(trim/lowercase) 선행 필요 — 현재는 보수적 allEntries.

## 테스트 (총 134, 이전 T2-12 126 → +8)
- `CacheBehaviorTest` 신규 8 (`@SpringBootTest` + properties override + `@MockitoBean` repository):
  - CacheManager 스모크 1 — CaffeineCacheManager 빈 등록 + 세 캐시 이름
  - CategoryNamesCache 2 — 캐시 히트 / ensureExists 후 무효화
  - CategoryAliasCache 3 — 캐시 히트 / add 후 무효화 / null/blank는 condition으로 캐시 자체 skip
  - IngredientVisibleCache 2 — 캐시 히트 / updateCategory 후 무효화
- 기존 126 테스트 영향 0 — `application-test.yaml`의 `cache.type=none`으로 NoOp.

---

# 다음 단계

## (보류) T2-13 Actuator + 모니터링 — 배포 직전 일괄 처리

사용자 결정 (2026-05-23): Actuator는 배포 직전에 도입. 지금 도입해도 외부 Prometheus/Grafana가 없으면 가시성 0 그대로라 효용이 약함. Caffeine 캐시 통계(`recordStats()` + endpoint)도 같이 처리.

## 이후 후보 (improvements.md 참조)

- T3-20 REST API + OpenAPI (수익화 단계 4 데이터 라이선싱 선행)
- T3-15 후속 — 즐겨찾기 / 팔로우 / 작성자 프로필
- T3-14 비번 재설정 / 이메일 인증 / 소셜 로그인
- T3-16 후속 — 조리 스텝 / 태그 / 갤러리
- T3-21 i18n + 접근성
- (보류) EC2 실제 배포 + 배포 직전 T2-13 Actuator
- (보류) T1-4 외부 저장소 실제 통합 — AWS SM / Vault (인스턴스 ≥ 2 또는 시크릿 ≥ 10 시점)

---

# 수익화 계획

자세한 결정 근거와 **의도적으로 안 하는 것** / **핵심 철학**은 [monetization.md](monetization.md) 참조. 여기서는 **진행 추적 + 기술 백로그와의 종속 관계**만 다룬다.

## 단계별 진행 상태

| 단계 | 모델 | 상태 | 시점 / 메모 |
|---|---|:---:|---|
| 0 | 쿠팡 파트너스 (`AffiliateLinkBuilder`, ingredient 목록의 "쿠팡 검색" 컬럼) | ✅ | Stage A-3 완료 (2026-05-21) |
| 1 | Google AdSense (광고 영역 제한 룰 준수) | ⏳ 대기 | 콘텐츠/트래픽 누적 후 |
| 2 | 제휴 확장 — 네이버 쇼핑 페이 → 11번가 → (선택) 마켓컬리 | ⏳ 대기 | AdSense와 병행 |
| 3 | Freemium 프리미엄 구독 (월 2,900~4,900원) | 🤔 보류 | MAU 5,000 + 차별화 기능 (T3-19) 선행 |
| 4 | 데이터 라이선싱 / API (B2B 식자재 가격 시계열) | 🤔 보류 | T3-19 12개월 누적 + T3-20 선행 |
| ∞ | B2B 외식업 SaaS — 별도 제품 분리 가능성 | 📌 검토 | 6-12개월 뒤 정기 리뷰 |

## 선행 종속 (각 단계 활성화 조건)

| 단계 | 기술 백로그 선행 (improvements.md) | 비기술 선행 |
|---|---|---|
| **1 AdSense** | — (광고 영역 위치 결정만) | 트래픽 누적 / 개인정보처리방침 정비 / 광고 표시 명시 (M1, M2) |
| **2 제휴 확장** | `AffiliateLinkBuilder` 인터페이스 추출 → `NaverAffiliateLinkBuilder` 등 다중 채널 구현 | 채널별 어필리에이트 가입/심사 (M3) |
| **3 Freemium** | **T3-19** 가격 이력 (차트 차별화 가치) / 결제 모듈 (Stripe 또는 토스페이먼츠) — 신규 항목 | MAU 5,000 / 무료-유료 라인 결정 (M4, M5) |
| **4 데이터 API** | **T3-19** 가격 이력 12개월 누적 / **T3-20** REST API + OpenAPI / **T3-18** 카테고리 정규화 (이미 ✅) | 약관/PII 정책 정비 (M7) / 데이터 SLA 정의 |
| **∞ B2B** | (Pivot 결정 후 별도 — B2C 코드 재사용 어려움) | B2C 트래픽 정체 / 사장 사용자 비율 증가 / 경쟁자 진입 — 트리거 신호 모니터링 (M6) |

## 핵심 관찰 — 다음 우선순위 후보

- **T3-19 (가격 이력)** 은 단계 3(Freemium 차트 차별화) + 단계 4(데이터 라이선싱)를 둘 다 활성화하는 **수익화 ROI 최고 백로그**. 누적 시간이 필요한 항목(12개월 누적)이라 **빨리 시작할수록 단계 4 진입이 빨라짐**.
- **현재 단계 0 운영 중** — 쿠팡 파트너스 매출/CTR 모니터링이 있어야 단계 1 진입 의사결정 가능. 정량 지표가 없으니 우선 부팅된 트래픽 누적 자체가 액션 아이템.
- **단계 1 AdSense**는 기술 선행이 사실상 없음 — 가장 빠른 추가 수익 채널. 단, 신뢰 손상 비용 고려해 광고 영역 결정(M2)이 선행.

## 직접 행동 (현재)

| 시점 | 액션 |
|---|---|
| **지금** | 단계 0 운영, 트래픽/매출 메트릭 누적 시작 (수익 메트릭 추적 자체가 미구현 — Actuator(T2-13)와 함께 검토 가치) |
| **트래픽 임계점 넘으면** | AdSense 신청 + 광고 영역 결정 (M1, M2) → 단계 1 진입 |
| **AdSense 안정화 후** | `AffiliateLinkBuilder` 인터페이스 추출 → 네이버 어필리에이트 추가 (단계 2) |
| **T3-19 착수 결정 시점** | Freemium/데이터 API 진입 시계 시작 |

## Open Questions M1-M7

비즈니스 결정 미확정 7개 — AdSense 도입 시기/위치, 프리미엄 단가, B2B Pivot 트리거 신호, 가격 이력 데이터 라이선싱 동의 절차 등. 자세한 내용 [monetization.md § Open Questions](monetization.md) 참조.

## 의도적으로 안 하는 모델 (요약)

[monetization.md](monetization.md)에 자세하지만 핵심만: **인터스티셜 광고, 레시피 잠금 + 결제 강요, 옵트인 없는 이메일/푸시 광고, 개인정보 판매, 암호화폐/NFT 토큰화**. 사용자 신뢰 자산 > 단기 수익.
