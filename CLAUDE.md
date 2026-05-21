# coastCalculator

음식 재료 가격 계산기. 관리자가 등록한 식재료 가격 데이터를 기반으로 사용자가 레시피를 입력하면 1g당 가격으로 원가를 계산해 보여주는 웹 서비스. **레시피 공유 허브** 컨셉 — 모두가 공개 레시피를 둘러보고, 인증 사용자는 작성, 소유자만 수정/삭제.

**핵심 인사이트**: 원가 계산은 보통 업소용 대용량(예: 밀가루 20kg) 기준이므로, 제품 가격이 아니라 **1g(또는 1ml)당 가격**으로 정규화해서 저장 → 어떤 단위 제품이든 공정 비교 가능.

> 작업 진행 상태 / Task별 상세 / Open Questions / Verification 체크리스트는 [docs/plan.md](docs/plan.md) 참조.

---

# 스택

`coastCalculator/coastCalculator/` (Spring Initializr 생성물):
- Java 25, Spring Boot 4.0.6, Gradle
- 의존성: Spring Web MVC, Data JPA, Security, Thymeleaf, Validation, Lombok, MySQL Driver, thymeleaf-extras-springsecurity6
- 패키지: `com.goosepl.coastCalculator`
- HTTP 클라이언트: 내장 `RestClient` (Jsoup 미사용, 크롤링 폐기)

---

# 설계 결정

| # | 항목 | 결정 |
|---|---|---|
| 1 | 데이터 소스 | 네이버 검색 API (쇼핑) — 크롤링 X |
| 2 | 갱신 전략 | 하이브리드 — DB 캐시 + TTL 만료 시에만 API 호출 |
| 3 | 인증 | 자체 회원가입/로그인 (ID+PW, BCrypt + Form Login) |
| 4 | 재료 매칭 | 카테고리 기반 — `Ingredient.category` 필드 |
| 5 | 저장 데이터 | 제품명, 제품 가격, 1g당 가격, 카테고리 |
| 6 | 단위 파싱 | `title`에서 정규식으로 `g/kg/mg/ml/l` 추출. 파싱 실패 시 저장 제외 |
| 7 | TTL | 24시간 (`application.yaml`에서 변경 가능) |
| 8 | 무게/부피 혼재 | `unit` 필드로 G/ML 구분, 같은 카테고리 내 분리 조회 |
| 9 | 원가 표시 | 전체 원가 + 인분당 단가 둘 다 |
| 10 | 재료 선택 정책 | 기본 LOWEST, 사용자가 AVERAGE/HIGHEST로 토글 (`PricingPolicy` enum) |
| 11 | 카테고리 관리 | 자동 X — 관리자가 개별 제품마다 수동 지정 (제품은 Naver fetch, `category`만 관리자 입력) |
| 12 | 권한 분리 | `ROLE_USER`는 재료 조회만. Naver fetch / 카테고리 수정 / 삭제는 `ROLE_ADMIN` 전용 |
| 13 | 익명 접근 | 익명에 홈/상세/검색 공개. 작성/수정/삭제만 인증 필요 |
| 14 | 검색 범위 | 레시피 이름만 — `Recipe.name LIKE '%keyword%'` |

## 표준 규칙

- **Naver fetch upsert**: `naverProductId` 존재 시 → `title/price/totalAmount/pricePerGram/image/mallName/link/fetchedAt` 업데이트, **`category`는 절대 건드리지 않음**. 신규 시 → `category=null`로 insert
- **`category=null` 행은 사용자에게 노출 X** (분류 안 된 raw 데이터)
- **관리자 시드**: 부팅 시 `admin / admin123!!` (`ROLE_ADMIN`) 멱등 생성 — 이미 있으면 skip, 비밀번호 덮어쓰기 X (운영 중 변경 가능성 보존)
- **관리자가 수정 가능한 필드**: `category`만. 나머지(title/price/totalAmount/unit/image/mallName/link)는 Naver 응답 그대로
- **소유자 체크**: 레시피 수정/삭제는 작성자 본인만. 상세 조회는 모두 가능
- **본인 레시피 편집 분기**: 템플릿에서 `${recipe.user.username == #authentication.name}` 로 수정/삭제 버튼 노출
- **소유권 위반**: `AccessDeniedException` → 커스텀 403 페이지

---

# 패키지 구조 (`com.goosepl.coastCalculator` 하위)

```
config/                  SecurityConfig, NaverApiProperties
domain/user/             User, Role, UserRepository, UserService, CustomUserDetailsService, DataInitializer
domain/ingredient/       Ingredient, Unit (enum), IngredientRepository, IngredientService
domain/recipe/           Recipe, RecipeIngredient, RecipeRepository, RecipeService
domain/recipe/cost/      RecipeCostCalculator, PricingPolicy enum
external/naver/          NaverShoppingClient (인터페이스), RealNaverShoppingClient, MockNaverShoppingClient
external/naver/parser/   UnitParser
web/                     AuthController, HomeController, IngredientController (read-only)
web/admin/               AdminIngredientController
web/error/               GlobalExceptionHandler
```

---

# 도메인 모델 (JPA 엔티티)

## `User`
| 필드 | 타입 | 비고 |
|---|---|---|
| id | Long | PK, auto |
| username | String | unique, not null |
| password | String | BCrypt 해싱 |
| role | enum (USER, ADMIN) | |
| createdAt, updatedAt | LocalDateTime | `@CreationTimestamp` |

## `Ingredient` (네이버 쇼핑 결과 + 관리자 카테고리)
| 필드 | 타입 | 비고 |
|---|---|---|
| id | Long | PK |
| naverProductId | String | unique — Naver `productId`, upsert 키 |
| title | String | 제품명 (Naver 응답 원본) |
| category | String | **nullable** — 관리자가 개별 지정. fetch 직후 비어있음 |
| price | int | 제품 가격 (원) |
| totalAmount | BigDecimal | `UnitParser`가 `title`에서 추출 |
| unit | enum (G, ML) | 무게/부피 구분 |
| pricePerGram | BigDecimal | `price / totalAmount`, 저장 시 계산 (scale=4) |
| image, mallName, link | String | Naver 응답 메타데이터 |
| fetchedAt | LocalDateTime | Naver fetch 시각, 관리자 UI에 표시 |
| createdAt, updatedAt | LocalDateTime | |

## `Recipe`
| 필드 | 타입 | 비고 |
|---|---|---|
| id | Long | PK |
| user | User | FK, LAZY |
| name | String | 레시피명 |
| servings | int | 인분 |
| createdAt, updatedAt | LocalDateTime | |

## `RecipeIngredient` (Recipe ↔ Ingredient 중간)
| 필드 | 타입 | 비고 |
|---|---|---|
| id | Long | PK |
| recipe | Recipe | FK, LAZY |
| categoryName | String | 사용자가 입력한 재료명 = 카테고리 (자유 입력) |
| selectedIngredient | Ingredient | FK, nullable — 명시적 선택 시만 |
| amount | BigDecimal | 필요한 양 |
| unit | enum (G, ML) | 사용자가 카테고리에 맞춰 선택 |
| ordering | int | 표시 순서 |

※ `amount` + `unit` 분리로 부피 재료(물/간장/식용유)도 원래 단위로 입력 가능. 원가 계산 시 같은 `unit`인 Ingredient만 후보로.

---

# 핵심 비즈니스 흐름

## 흐름 1: 관리자 — Naver fetch + 카테고리 지정

```
GET  /admin/ingredients                     → 전체 목록 (미지정 우선 옵션)
GET  /admin/ingredients/fetch               → Naver 검색 폼
POST /admin/ingredients/fetch               → IngredientService.fetchAndUpsert(keyword)
GET  /admin/ingredients/{id}/edit           → 카테고리 수정 폼
POST /admin/ingredients/{id}                → 카테고리 업데이트
POST /admin/ingredients/{id}/delete         → 삭제
```

### `IngredientService.fetchAndUpsert(keyword)`
```
items = naverClient.search(keyword)
parsed = items.map { UnitParser.parse(title) → totalAmount/unit 추출 }
parsed.filter { 파싱 성공 }.forEach { item ->
  existing = repo.findByNaverProductId(item.naverProductId)
  if existing != null:
    existing.title/price/totalAmount/unit/pricePerGram/image/mallName/link 갱신
    existing.fetchedAt = now
    // category 는 건드리지 않음 ← 핵심 규칙
  else:
    insert new (category=null, fetchedAt=now)
}
```

## 흐름 1-B: 사용자 재료 조회 (TTL 하이브리드)

```
GET /ingredients                  → 카테고리 지정된 재료 전체 목록
GET /ingredients?category=밀가루   → IngredientService.viewByCategory("밀가루")
```

`viewByCategory(category)`:
1. `rows = repo.findByCategory(category)`
2. 비었거나 stale(`fetchedAt < now - 24h`) 있으면 → `fetchAndUpsert(category)` 트리거
3. 다시 `repo.findByCategory(category)` 반환

## 흐름 2: 단위 파싱 (`UnitParser`)

- 정규식: `(\d+(?:[.,]\d+)?)\s*(kg|g|mg|L|l|ml)\b` (case-insensitive)
- kg → g, L → ml 정규화
- 매칭 여러 개 (예: "1kg×3봉"): 첫 매칭만 (추후 곱셈 처리 확장 여지)
- 매칭 실패 → skip
- `pricePerGram = price / totalAmount` (BigDecimal, scale=4)

## 흐름 3: 원가 계산 (`RecipeCostCalculator`)

```
입력: Recipe, PricingPolicy (LOWEST 기본 / AVERAGE / HIGHEST)

각 RecipeIngredient에 대해:
  if selectedIngredient != null:
    pricePerGram = selectedIngredient.pricePerGram          // 직접 고른 경우 정책 무시
  else:
    candidates = ingredientRepo.findByCategoryAndUnit(categoryName, unit)
    pricePerGram = switch (policy):
      LOWEST   -> min(candidates.pricePerGram)
      AVERAGE  -> avg(candidates.pricePerGram)
      HIGHEST  -> max(candidates.pricePerGram)
  cost = pricePerGram × amount

총합 = Σ cost
인분당 = 총합 / servings
```

**매칭 실패** (해당 `category/unit`로 등록된 Ingredient 0건): "재료 없음" 표시, 총합에서 제외, 경고 카운트 노출.

## 흐름 4: 관리자 시드 (`DataInitializer`)

```
if (!userRepository.existsByUsername("admin")):
    User admin = User.builder()
        .username("admin")
        .password(passwordEncoder.encode("admin123!!"))
        .role(Role.ADMIN)
        .build()
    userRepository.save(admin)
```

## 라우팅 (레시피 허브)

| URL | 역할 |
|---|---|
| `GET /` | 허브: 검색바 + 최근 레시피 N개 (`createdAt DESC`). `?q=keyword`로 검색 |
| `GET /recipes` | 내 레시피 목록 |
| `GET /recipes/new` | 신규 작성 (인증 필요) |
| `POST /recipes` | 생성 |
| `GET /recipes/{id}` | 누구나 상세 조회. 소유자에게만 수정/삭제 버튼 |
| `GET /recipes/{id}/edit` | 편집 폼 (소유자만) |
| `POST /recipes/{id}` | 업데이트 (소유자만) → detail로 redirect |
| `POST /recipes/{id}/delete` | 삭제 (소유자만) |

`RecipeRepository`:
- `findTopNByOrderByCreatedAtDesc(Pageable)` 또는 `findAll(Pageable)` with Sort
- `findByNameContainingIgnoreCaseOrderByCreatedAtDesc(String, Pageable)` — 검색
- 두 쿼리에 `@EntityGraph(attributePaths={"user","ingredients"})` 적용

`RecipeService`:
- `findRecent(int limit)` / `searchByName(String, int)` — 공개
- `findForView(Long id)` — 공개 상세 (소유권 체크 X, ingredients/user EAGER fetch)
- `findMine` — 편집·삭제 전용

---

# 외부 의존성 / 설정

## MySQL — Docker

`docker-compose.yml` (프로젝트 루트):
```yaml
services:
  mysql:
    image: mysql:8.4
    container_name: coast-calculator-mysql
    restart: unless-stopped
    environment:
      MYSQL_ROOT_PASSWORD: ${DB_ROOT_PASSWORD:-rootpass}
      MYSQL_DATABASE: coast_calculator
      MYSQL_USER: ${DB_USERNAME:-coast}
      MYSQL_PASSWORD: ${DB_PASSWORD:-coastpass}
    ports:
      - "3309:3306"   # 호스트 3309 (3306 충돌 회피)
    volumes:
      - coast-mysql-data:/var/lib/mysql
    command: --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci

volumes:
  coast-mysql-data:
```
실행: `docker compose up -d`

## `application.yaml`

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3309/coast_calculator?serverTimezone=Asia/Seoul&characterEncoding=UTF-8
    username: ${DB_USERNAME:coast}
    password: ${DB_PASSWORD:coastpass}
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    hibernate:
      ddl-auto: update         # 초기 개발은 update, 운영 진입 시 validate
    properties:
      hibernate:
        format_sql: true
    show-sql: true
    open-in-view: false

naver:
  api:
    base-url: https://openapi.naver.com/v1/search/shop.json
    client-id: ${NAVER_CLIENT_ID:}
    client-secret: ${NAVER_CLIENT_SECRET:}
    ttl-hours: 24
    display: 100         # 네이버 최대 100
    mock-enabled: true   # 키 발급 전엔 true

server:
  error:
    include-message: never
    include-stacktrace: never
    include-binding-errors: never
    whitelabel:
      enabled: false
```

API 키는 `application-local.yaml` (.gitignore)로 분리. 실행: `.\gradlew bootRun --args='--spring.profiles.active=local'`.

## 네이버 API 키 미발급 대응

`NaverShoppingClient`를 인터페이스로 추상화:
- `RealNaverShoppingClient` — 실제 호출, `@ConditionalOnProperty(name="naver.api.mock-enabled", havingValue="false")`
- `MockNaverShoppingClient` — 더미 응답 (밀가루/설탕/소금 등 5~10개), `@ConditionalOnProperty(name="naver.api.mock-enabled", havingValue="true", matchIfMissing=true)`

키 발급: https://developers.naver.com/ → 앱 등록 → "검색" API 권한 → Client ID/Secret → `application-local.yaml` 주입 + `mock-enabled: false`.

---

# 에러 처리

`web/error/GlobalExceptionHandler` (`@ControllerAdvice`):
- `IllegalArgumentException` — UserService/RecipeService/IngredientService 검증 실패 → WARN
- `AccessDeniedException` — 소유자 체크 실패, Spring Security 403 → 403 페이지
- `IllegalStateException` — 정상 흐름에선 발생 X → ERROR
- `MethodArgumentNotValidException` — `@Valid` 실패 (JSON API 대비)
- `NoResourceFoundException` (Spring 6+) — 404
- 그 외 `Exception` — 500, ERROR

에러 페이지 (`templates/error/{status}.html` 위치 시 Spring Boot 자동 매칭):
- `403.html`, `404.html`, `500.html`, `error.html` (fallback)
