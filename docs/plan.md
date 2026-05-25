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
| T1-6 | 핵심 통합 테스트 4종 (H2+Mockito+@DataJpaTest+@SpringBootTest, 56 테스트) | ✅ |
| T2-9 | Naver API 타임아웃(5s/10s) + Spring Retry(3회 지수 backoff) + Recover fallback | ✅ |
| T3-22 후속 | GitHub Actions CI/CD (`.github/workflows/ci.yml`) — test + GHCR 이미지 푸시 | ✅ |
| T3-17 | selectedIngredient UI — "이 제품으로 고정" 드롭다운 + 카테고리/단위 검증 (64 테스트) | ✅ |
| T2-7 | 페이지네이션 — 홈/검색/내 레시피, 페이지 크기 12, prev/next + ±2 페이지 번호 (65 테스트) | ✅ |
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

# 다음 단계

## (보류) T2-13 Actuator + 모니터링 — 배포 직전 일괄 처리

사용자 결정 (2026-05-23): Actuator는 배포 직전에 도입. 지금 도입해도 외부 Prometheus/Grafana가 없으면 가시성 0 그대로라 효용이 약함.

## 이후 후보 (improvements.md 참조)

- T2-8 비동기 / 스케줄러 기반 Naver refetch (T2-9와 짝)
- T2-11 N+1 점검 후속 — T2-7에서 발생 가능한 `ToMany + Pageable` in-memory paging 경고 정리 (two-step 쿼리 패턴)
- T3-18 카테고리 정규화 (마스터 테이블 / synonym) — T3-17과 자연 연결
- T1-4 시크릿 외부 저장소 (현재 application-local.yaml 분리만 — 운영급 Vault/AWS Secrets 미적용)
- (보류) EC2 실제 배포 + 배포 직전 T2-13 Actuator
