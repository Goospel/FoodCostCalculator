# coastCalculator

음식 재료 가격 계산기. 관리자가 등록한 식재료 가격 데이터를 기반으로 사용자가 레시피를 입력하면 1g당 가격으로 원가를 계산해 보여주는 웹 서비스. **레시피 공유 허브** 컨셉 — 익명이 공개 레시피를 둘러보고, 인증 사용자가 작성, 소유자만 수정/삭제.

**핵심 인사이트**: 원가 계산은 보통 업소용 대용량(예: 밀가루 20kg) 기준이라 제품 가격이 아니라 **1g(또는 1ml)당 가격**으로 정규화 저장 → 어떤 단위 제품이든 공정 비교 가능.

---

# 문서 안내 (먼저 여기서 찾을 것)

| 문서 | 언제 보나 |
|---|---|
| [docs/plan.md](docs/plan.md) | 작업 진행 상태 / Task별 상세 / Open Questions / Verification |
| [docs/troubleshooting.md](docs/troubleshooting.md) | **새 에러/낯선 동작 만나면 무조건 여기 먼저**. 새 함정은 해결 후 TS-N으로 기록 |
| [docs/improvements.md](docs/improvements.md) | 배포 readiness 백로그 (Tier 1/2/3) |
| [docs/monetization.md](docs/monetization.md) | 수익화 모델 / B2B 장기 비전 후보 |
| [docs/deployment.md](docs/deployment.md) | EC2 배포 가이드 |

---

# 워크플로우 규칙 (반드시 지킬 것)

## PR 만들기 전
1. **항상** `gh pr list --repo Goospel/FoodCostCalculator --state all --limit 10` 으로 **다음 PR 번호 + 진행 중 PR 유무** 확인.
2. PR 본문 또는 머지 메시지에 "이번 PR이 #N" 식으로 번호 명시.
3. 새 작업은 항상 **main 최신화 → 새 브랜치**(`feat/...`, `docs/...`, `fix/...`).
4. **OPEN PR이 이미 있는데 새 PR이 `docs/plan.md` 또는 `docs/improvements.md`를 만질 거면** — 두 PR의 진행 추적 영역(진행 상태 표 / "다음 단계" 섹션 / "이후 후보" 큐)이 같아 거의 100% 충돌. 우선순위:
   - **(권장)** OPEN PR 먼저 머지 후 main pull → 새 작업 시작
   - 시간 급하면 OPEN PR을 base branch로 새 PR 생성 (stacked PR — `gh pr create --base feat/xxx`)
   - 둘 다 안 되면 main 기반으로 진행하되 **rebase + 수동 충돌 해결 각오** (이전 사례: [troubleshooting TS-12](docs/troubleshooting.md))
   - 코드 변경은 보통 서로 다른 파일이라 충돌 없음 — 문서만 주의

## 에러/낯선 동작 만났을 때
1. **`docs/troubleshooting.md` 먼저 확인**. 같은/비슷한 TS-N이 있는지.
2. 거기 없는 새 함정이면 해결 후 **반드시 TS-N으로 추가** (형식: **증상 → 진단 → 해결 → 교훈**).

## 코드 변경
- 운영 `application.yaml` 수정은 신중 — Docker/CI/운영 영향. 로컬 전용은 `application-local.yaml` 사용 (`.gitignore`).
- DB 스키마 변경은 **Flyway V2/V3 마이그레이션 필수**. `ddl-auto: validate` 유지.
- 모든 컬렉션 페치 경로에 `@EntityGraph` 명시 — `open-in-view: false`라 트랜잭션 밖 LAZY 접근 시 `LazyInitException`.

---

# 스택

Java 25, Spring Boot 4.0.6, Gradle. Spring Web MVC + Data JPA + Security + Thymeleaf + Validation + Lombok + MySQL Driver + thymeleaf-extras-springsecurity6. HTTP 클라이언트는 내장 `RestClient` (Jsoup/크롤링 X). 패키지 루트: `com.goosepl.coastCalculator`.

> Spring Boot 4부터 autoconfigure 모듈 분리 → 새 라이브러리 추가 시 `flyway-core` 단독으론 안 됨, `spring-boot-starter-*` 필수 (자세한 건 troubleshooting TS-1).

---

# 패키지 구조

```
config/           SecurityConfig, NaverApiProperties, AffiliateProperties, RetryConfig,
                  AsyncConfig (T2-8 @EnableAsync/@EnableScheduling),
                  ProductionSecretsValidator (T1-4 @Profile("prod")), WebMvcConfig
domain/user/      User, Role, UserRepository, UserService, CustomUserDetailsService, DataInitializer,
                  auth/(LoginAttemptService, AuthenticationEventListener)
domain/ingredient/ Ingredient, Unit, IngredientRepository, IngredientService,
                  IngredientRefetchService (T2-8 @Async/@Scheduled),
                  IngredientPriceHistory, IngredientPriceHistoryRepository (T3-19)
domain/category/  Category, CategoryRepository, CategoryService (T3-18),
                  CategoryAlias, CategoryAliasRepository, CategoryAliasService (T3-18.2)
domain/recipe/    Recipe, RecipeIngredient, RecipeRepository, RecipeService,
                  cost/(RecipeCostCalculator, PricingPolicy, RecipeCostResult)
domain/comment/   Comment, CommentService, ...
domain/like/      RecipeLike, RecipeLikeService
external/naver/   NaverShoppingClient (interface), Real/MockNaverShoppingClient, parser/UnitParser
storage/          ImageStorageService, LocalImageStorageService, StorageProperties
web/              AuthController, HomeController, IngredientController, RecipeController, ...
web/admin/        AdminIngredientController, AdminCategoryAliasController (T3-18.2)
web/error/        GlobalExceptionHandler
```

엔티티 필드/관계 일람은 **코드 직접 참조** (`src/main/java/.../domain/`).

---

# 절대 규칙 (invariant)

## Naver fetch upsert (`IngredientService.fetchAndUpsert`)
- `naverProductId` 존재 시 → `title/price/totalAmount/pricePerGram/image/mallName/link/fetchedAt` 업데이트. **`category`는 절대 안 건드림**.
- 신규 시 → `category=null` insert. **`category=null` 행은 사용자에게 노출 X** (관리자만).
- **T3-19 가격 이력 적재**: 기존 갱신 시 `pricePerGram` 변동(BigDecimal.compareTo != 0)이면 `IngredientPriceHistory` row 추가. 신규는 시작 시점 무조건 적재. 동일 단가 fetch는 적재 X (옵션 B — 데이터 효율 + "이 시점에 변동" 의미 명확).

## 관리자
- `DataInitializer`가 부팅 시 `admin` 멱등 생성. 비밀번호: env `INITIAL_ADMIN_PASSWORD` 우선 → 미설정 시 `SecureRandom` 16자 + WARN 로그 1회. 로컬 개발은 `application-local.yaml`에 `app.admin.initial-password`로 고정.
- 관리자가 수정 가능한 ingredient 필드는 **`category`만**. 나머지는 Naver 응답 원본.

## 소유권
- 레시피 수정/삭제는 작성자 본인만. 상세 조회는 누구나.
- 본인 분기는 템플릿에서 `${recipe.user.username == #authentication.name}`.
- 위반 시 `AccessDeniedException` → 커스텀 403.

## 원가 계산 (`RecipeCostCalculator`)
- `RecipeIngredient.selectedIngredient != null` → 그 제품 단가만 사용 (정책 무시, alias 풀이도 X).
- null → `CategoryAliasService.resolve(categoryName)` 후 `IngredientRepository.findByCategoryAndUnit(resolved, unit)` 후보 → `PricingPolicy` (LOWEST/AVERAGE/HIGHEST)로 단가 결정.
- 매칭 0건 → "재료 없음" 표시, 총합 제외, 경고 카운트 노출.

## 카테고리 alias (T3-18.2)
- **저장은 사용자 의도 그대로 유지** — `RecipeIngredient.categoryName="박력분"`이면 DB에도 "박력분".
- **매칭 단계에서만 풀어 사용** — `RecipeCostCalculator`/`resolveSelectedIngredient`가 `resolve()` 호출.
- `resolve(input)` 우선순위: (1) categories.name 있으면 input(canonical 우선) → (2) aliases.alias 있으면 canonical.name → (3) 없으면 input.
- alias 등록 검증: blank / 자기자신 / canonical과 같은 이름 / 중복 / canonical 미존재 모두 거부.

## selectedIngredient 검증 (T3-17)
- 사용자가 "이 제품으로 고정" 선택 시 행 categoryName(대소문자 무시)/unit 일치해야 저장. 불일치/카테고리 미부여/미존재 ID → `IllegalArgumentException`으로 저장 거부 (자동 덮어쓰기·무시 X).

## TTL 하이브리드 + 비동기 refetch (T2-8)
- `viewByCategory(c)`: 항상 캐시 즉시 반환 (`@Transactional(readOnly=true)`, Naver 호출 블로킹 X).
- stale(빈 결과 or `fetchedAt < now - 24h`) 감지 시 `IngredientRefetchService.triggerAsyncRefetch(c)` 백그라운드만 트리거 → 결과는 다음 사용자 진입에 반영.
- `@Scheduled(fixedDelay=1h)` `scheduledRefresh()`가 stale 카테고리 일괄 갱신 (TTL 24h 대비 보수적).
- 카테고리별 `ConcurrentHashMap<String, AtomicBoolean>` 락 — 같은 카테고리 중복 refetch 방지. `naverRefetchExecutor` 풀(core 2/max 4/queue 100, DiscardPolicy)로 톰캣 스레드 분리.

## Naver 키 미발급 대응
- `NaverShoppingClient` 인터페이스 → Real/Mock 구현. `naver.api.mock-enabled=true`면 Mock 활성.
- Real에는 connect 5s / read 10s 타임아웃 + Spring Retry 3회 지수 backoff(1→2→4s) + `@Recover`로 빈 리스트 fallback (T2-9).

## 보안
- 비밀번호: 최소 8자 + 영문/숫자 (`@Pattern`).
- Brute force: username당 5회 실패 → 15분 잠금 (`LoginAttemptService`, 메모리 `ConcurrentHashMap`). 15분 경과 시 자동 해제.

## 운영 시크릿 (T1-4)
- 운영은 `SPRING_PROFILES_ACTIVE=prod` (docker-compose.prod.yml 자동 주입) → `application-prod.yaml` 활성 + `ProductionSecretsValidator` 부팅 검증.
- 검증 실패 시 부팅 거부:
  1. DB 자격증명이 디폴트(`coast`/`coastpass`) 그대로면 거부
  2. `INITIAL_ADMIN_PASSWORD` 빈 채로 두면 거부 (랜덤 비번 로그 노출은 운영에서 금지 — 회전 어려움 + 로그 유출 시 즉시 침해)
  3. `naver.api.mock-enabled=false`인데 `NAVER_CLIENT_ID`/`SECRET` 비어있으면 거부
- 위반 모두 모아 IllegalStateException 한 번에 표시. 시크릿 관리/회전/외부 저장소 통합 hook: [docs/deployment.md § 9](docs/deployment.md).

## 화면 정책
- **JS 없음 정책** (Open Q #24) — 모든 인터랙션은 폼 POST + redirect. 동적 UI는 폼 GET 시점에 모델로 미리 담아 렌더링 (예: T3-17 selectedIngredient `<optgroup>`).
- 이미지 업로드: jpg/jpeg/png/webp 화이트리스트, 5MB. 로컬 `./uploads/` + Spring `/uploads/**` 정적 서빙.

---

# 핵심 라우팅 (빠른 ref)

| URL | 역할 |
|---|---|
| `GET /` | 허브: 검색 + 최근 레시피 N개. `?q=keyword` |
| `GET /recipes/{id}` | 누구나 상세. 소유자에게만 수정/삭제 버튼 |
| `GET /recipes/new`, `/{id}/edit`, `POST /{id}/delete` | 인증 / 소유자만 |
| `GET /admin/**` | `ROLE_ADMIN` |
| `GET /ingredients` | 카테고리 부여된 재료만 (`category != null`). 익명 OK |
| `POST /recipes/{id}/like`, `POST /recipes/{id}/comments` | 인증 사용자 |

전체 보안 룰은 `SecurityConfigTest`(16 케이스)가 명세이자 검증.

---

# 외부 의존성

- **MySQL**: Docker, 호스트 3309 ↔ 컨테이너 3306, 스키마 `coast_calculator`. 실행 `docker compose up -d`.
- **Naver Open API**: `application-local.yaml`(`.gitignore`)에 키 + `--spring.profiles.active=local`. 키 미발급 시 Mock 자동 활성.
- **GHCR**: CI(`.github/workflows/ci.yml`)가 main push 시 `ghcr.io/goospel/coastcalculator:latest` + `sha-*` 두 태그 푸시. 권한은 패키지 settings에서 repo Write access 필요 (TS-10).

설정 전문은 코드 직접 참조: `src/main/resources/application.yaml`, `docker-compose.yml`, `Dockerfile`, `.github/workflows/ci.yml`.

---

# 에러 처리 (`web/error/GlobalExceptionHandler`)

`@ControllerAdvice`로 통합. `IllegalArgumentException`(WARN), `AccessDeniedException`(403), `IllegalStateException`(ERROR), `MethodArgumentNotValidException`(@Valid), `NoResourceFoundException`(404), 기타 `Exception`(500, ERROR).

에러 페이지: `templates/error/{403,404,500,error}.html`. `server.error.include-message: never` (운영 안전성).
