# coastCalculator — 작업 계획 / 진행 상태

안정적인 아키텍처·도메인·규칙은 [CLAUDE.md](../CLAUDE.md) 참조. 이 문서는 Task 진행 상태, Task별 상세, Open Questions, Verification 체크리스트 등 **진행하면서 갱신되는 내용**만 담음.

---

# 구현 순서 / 진행 상태

1. ~~**인프라** — `application.yaml`, `SecurityConfig`, MySQL 스키마~~ ✅
2. ~~**User 도메인** — 엔티티 + 회원가입/로그인~~ ✅
3. **Ingredient 도메인 + Naver API + admin seed + 권한 분리** ← Plan v3
4. **Recipe 도메인** (원가 계산 미포함, 단순 CRUD)
5. **레시피 허브 (Task 4.7)** — 공개 조회 + 검색 + 홈 페이지 전환
6. **원가 계산 (Task 5)**
7. **마무리 (Task 6)** ← 현재 단계

---

# Task 3: Ingredient 도메인 + Naver API + admin seed + 권한 분리

- 3-a. `DataInitializer` — admin 시드 (멱등)
- 3-b. `SecurityConfig` — `/admin/**` → `hasRole("ADMIN")`, `/ingredients` → `authenticated()`
- 3-c. `application.yaml`의 `naver:` 블록 — TTL 24h
- 3-d. `Ingredient` 엔티티 + `Unit` enum + `IngredientRepository` (`findByCategoryIsNotNull`, `findByCategory`, `findByNaverProductId`)
- 3-e. `UnitParser` — 정규식 기반 (단위 테스트 우선)
- 3-f. `NaverShoppingClient` 인터페이스 + Mock + Real (RestClient)
- 3-g. `IngredientService` — fetch/upsert, 카테고리 수정/삭제/조회
- 3-h. `AdminIngredientController` + Thymeleaf (목록, 검색 폼, 수정 폼)
- 3-i. `IngredientController` (사용자 read-only) + 리스트 (미지정 행 숨김)
- 3-j. `home.html` 네비게이션 admin 메뉴 (`sec:authorize="hasRole('ADMIN')"`)

## Critical Files (Task 3)

### 신규
- `domain/user/DataInitializer.java`
- `domain/ingredient/Ingredient.java`
- `domain/ingredient/Unit.java` — enum `G`, `ML`
- `domain/ingredient/IngredientRepository.java`
- `domain/ingredient/IngredientService.java`
- `domain/ingredient/dto/CategoryUpdateForm.java`
- `external/naver/NaverShoppingClient.java` (인터페이스)
- `external/naver/RealNaverShoppingClient.java`
- `external/naver/MockNaverShoppingClient.java`
- `external/naver/dto/NaverProduct.java`
- `external/naver/parser/UnitParser.java`
- `web/IngredientController.java`
- `web/admin/AdminIngredientController.java`
- 템플릿: `templates/ingredients/list.html`, `templates/admin/ingredients/{list,fetch,edit}.html`
- 테스트: `UnitParserTest.java`

### 수정
- `config/SecurityConfig.java` — `/admin/**` ROLE_ADMIN, `/ingredients` 인증 필요
- `resources/application.yaml` — TTL 24h 유지
- `resources/templates/home.html` — admin 메뉴 노출

---

# Task 4: Recipe 도메인 (단순 CRUD)

- 4-a. `Recipe`, `RecipeIngredient` 엔티티 + Repository
- 4-b. `RecipeService` — 본인 레시피만 (소유자 체크)
- 4-c. `RecipeForm` DTO — 폼 바인딩 (재료 행 N개)
- 4-d. `RecipeController` — `/recipes` 목록 / `/new` / `/{id}` 조회·수정 / `/delete`
- 4-e. Thymeleaf 템플릿 — list, form (고정 10행, 빈 행 무시, JS 없음)
- 4-f. `SecurityConfig` — `/recipes/**`는 이미 `authenticated()` 적용
- 4-g. home.html "내 레시피" 링크 (기존)

※ Task 4에서는 **원가 계산 미포함**. 단순 CRUD만.

---

# Task 4.5: Naver API 키 적용

- 4.5-a. `src/main/resources/application-local.yaml` 생성:
  ```yaml
  naver:
    api:
      client-id: <발급값>
      client-secret: <발급값>
      mock-enabled: false
  ```
- 4.5-b. `.gitignore`에 `application-local.yaml` 추가
- 4.5-c. 실행: `.\gradlew bootRun --args='--spring.profiles.active=local'`
- 4.5-d. 확인:
  - 부팅 로그: `The following 1 profile is active: "local"`
  - admin이 fetch 시 `[Mock]` 없이 실제 호출 로그
  - 실제 쇼핑몰 제품이 DB에 저장

---

# Task 4.7: 레시피 허브 (공개 조회 + 검색 + 홈 페이지 전환)

## 컨셉 변경 (사용자 요구)
- 단순 "내 원가 계산기" → **레시피 공유 허브** (GitHub처럼 모두가 공개 레시피 둘러봄)
- 로그인 직후 랜딩(=`/`)에 공개 레시피 목록 + 검색바
- 수정/삭제는 소유자 전용 (기존 유지)
- 새 레시피 생성은 인증 사용자 누구나

라우팅·서비스 변경은 CLAUDE.md의 "라우팅 (레시피 허브)" 섹션 참조.

## 템플릿
- `home.html` 대대적 개편 — 검색 폼 + 레시피 카드/리스트
- 신규 `recipes/detail.html` (Task 5에서 cost 섹션 추가)
- `recipes/list.html` (내 레시피)는 그대로

## Task 5와 경계
- 4.7: 허브 + 공개 조회 + 검색 + detail 페이지 골격
- 5: detail에 원가 계산 결과 섹션 + PricingPolicy 토글 추가

---

# Task 5: 원가 계산

- 5-a. `domain/recipe/cost/PricingPolicy.java` — enum
- 5-b. `IngredientRepository.findByCategoryAndUnit(String, Unit)` 추가
- 5-c. `RecipeCostCalculator` — 흐름 3 구현
- 5-d. `RecipeCostResult` DTO — 재료별 라인, 총합, 인분당, 매칭 실패 카운트
- 5-e. **detail/edit 페이지 분리**:
  - `GET /recipes/{id}` → `recipes/detail.html` (read-only + 원가 + Policy 드롭다운)
  - `GET /recipes/{id}/edit` → `recipes/form.html` (편집, 원가 없음)
  - `POST /recipes/{id}` → 업데이트 후 detail로 redirect
- 5-f. `RecipeController` — detail/edit 액션 분리
- 5-g. `templates/recipes/detail.html` 신규

## detail 페이지 동작
```
GET /recipes/{id}?policy=LOWEST(기본)
  → recipe 로드
  → RecipeCostCalculator.calculate(recipe, policy)
  → 결과:
     - 재료 라인별: categoryName / amount unit / 단가 / 행 소계 (매칭 실패 "재료 없음")
     - 총합 / 인분당 단가
     - 매칭 실패 행 N개 경고
  → Policy 드롭다운(LOWEST/AVERAGE/HIGHEST) 변경 시 GET 자체 리로드
```

---

# Task 6: 마무리 ← 현재 단계

※ `improvements.md`의 **T1-5 (글로벌 ExceptionHandler + 에러 페이지)** 동반 해결.

## 6-a. GlobalExceptionHandler
- `web/error/GlobalExceptionHandler.java` 신규 — `@ControllerAdvice`
- 각 예외 타입별 `@ExceptionHandler` → HTTP 상태 + 모델 + 템플릿
- 자세한 예외 매핑은 CLAUDE.md "에러 처리" 섹션 참조

## 6-b. 에러 페이지 템플릿
- `templates/error/403.html`, `404.html`, `500.html`, `error.html`

## 6-c. application.yaml production-safety
`server.error.*` 설정 (CLAUDE.md application.yaml 참조)

## 6-d. improvements.md 업데이트
T1-5 ✅ 체크, 해결일/커밋 기재

## 6-e. 통합 시나리오 매뉴얼 점검
- [ ] 익명 → 홈 → 검색 → 상세 보기 (성공)
- [ ] 익명 → `/recipes/{id}/edit` 직접 접근 → 로그인 리다이렉트
- [ ] 사용자 A → 회원가입 → 로그인 → 레시피 생성 → 원가 정책 토글
- [ ] 사용자 B → A의 레시피 상세는 보임, edit 직접 접근 시 **403 (커스텀 페이지)**
- [ ] admin 로그인 → 재료 fetch → 카테고리 부여 → 사용자 레시피 원가 정상 계산
- [ ] 존재하지 않는 `/recipes/99999` → **404 (커스텀 페이지)**
- [ ] DB 종료 상태에서 페이지 접근 → **500 (커스텀 페이지)**, 스택트레이스 노출 X

## Critical Files (Task 6)
- 신규: `web/error/GlobalExceptionHandler.java`
- 신규: `templates/error/{403,404,500,error}.html`
- 수정: `application.yaml` — `server.error.*`
- 수정: `docs/improvements.md` — T1-5 체크

---

# Open Questions — 확정된 답변

| # | 질문 | 답변 |
|---|---|---|
| 1 | 네이버 API 키 | 미발급 — Mock으로 개발, 키 받으면 yaml 토글 |
| 2 | MySQL 환경 | Docker — 호스트 3309 ↔ 컨테이너 3306, 스키마 `coast_calculator` |
| 3 | 카테고리 관리 | 관리자가 개별 제품마다 수동 지정 |
| 4 | 원가 표시 | 전체 + 인분당 둘 다 |
| 5 | 재료 선택 정책 | 기본 LOWEST, AVERAGE/HIGHEST 토글 |
| 6 | 관리자 워크플로 | Naver fetch로 raw 받은 뒤 admin UI에서 row별 카테고리 입력 |
| 7 | 관리자 시드 | `admin / admin123!!` (`ROLE_ADMIN`), 부팅 시 멱등 |
| 8 | Naver fetch 중복 | `naverProductId` 존재 시 다른 필드만 업데이트, `category` 보존 |
| 9 | TTL | 24시간, 사용자 조회 시 stale 자동 refetch |
| 10 | Recipe 재료 행 UI | 고정 10행, 빈 행 저장 시 무시, JS 없음 |
| 11 | RecipeIngredient 단위 | `amount` + `unit` 분리 |
| 12 | detail vs edit | 분리 — detail은 read-only + 원가 + Policy, edit은 별도 폼 |
| 13 | 매칭 실패 | 0건 시 "재료 없음" 표시, 총합 제외, 경고 카운트 |
| 14 | API 키 주입 | `application-local.yaml` + `.gitignore`, `--spring.profiles.active=local` |
| 15 | 익명 접근 | 익명에 홈/상세/검색 공개, 작성/수정/삭제만 인증 |
| 16 | 검색 범위 | 레시피 이름만 — `Recipe.name LIKE '%keyword%'` |

---

# Verification

## Task 3 종료 시 점검
- **admin 시드**: 첫 부팅 후 `users`에 `username='admin', role='ADMIN'` 한 행. 재부팅 시 그대로 (중복/덮어쓰기 X)
- **admin 로그인**: `admin / admin123!!` 성공
- **권한 차단**: USER로 `/admin/ingredients` 접근 시 403
- **Mock 동작**: `mock-enabled: true` 상태에서 fetch → 더미 리스트 → DB에 `category=null` 저장
- **카테고리 수정**: admin이 한 row 카테고리 "밀가루"로 저장 → DB 반영
- **사용자 조회 필터**: USER가 `/ingredients` → 카테고리 지정 행만, 미지정 숨김. fetch/수정 메뉴 안 보임
- **TTL 자동 refetch**: 24h 내 → DB만, 24h 경과 → 자동 `fetchAndUpsert` 트리거 (로그 확인)
- **카테고리 보존 (핵심)**: admin이 "오뚜기 밀가루"에 `category=밀가루` 부여 → 24h 뒤 refetch → 가격 갱신, `category` 유지
- **UnitParser 단위 테스트**: `"CJ 백설 다목적 밀가루 1kg"`, `"오뚜기 밀가루 20kg"`, `"500g"`, `"1.5L 식용유"`, 파싱 불가 케이스

## Task 4·5 검증
- 회원가입 → 로그인 → 레시피 생성 → 재료 추가("밀가루 500g") → 원가 조회
- 비로그인 `/recipes` 접근 시 로그인 리다이렉트
- 사용자 A의 레시피를 B가 조회/수정 불가

---

# 별도 산출물: `docs/improvements.md` (배포 readiness)

위치: `coastCalculator/coastCalculator/docs/improvements.md`

구성:
- **Tier 1 (배포 전 반드시)**: Flyway, admin 시드 보안, 비밀번호 정책 + brute force, 시크릿 외부화, 전역 예외 처리, 테스트 부재
- **Tier 2 (사용자 늘면 발목)**: 페이지네이션, 비동기/스케줄러 Naver fetch, 타임아웃/리트라이/서킷브레이커, 캐싱, N+1, optimistic locking, Actuator/모니터링
- **Tier 3 (성숙도)**: 이메일 인증/비번 재설정/소셜 로그인, 커뮤니티 기능, 레시피 메타(이미지/스텝/태그), selectedIngredient UI, 카테고리 정규화, 가격 이력, REST API, i18n/접근성, CI/CD/Dockerfile

각 항목: "현재 상태 / 왜 문제인지 / 해결 방향" 3섹션. 맨 아래 "현재 진행 상태 추적" 체크박스.
