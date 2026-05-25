# 배포 readiness 개선 백로그

> 진짜 서비스 배포 기준으로 본 미흡사항. 작업 진행하며 체크박스와 날짜로 추적.
>
> - **Tier 1** = 배포 전 반드시
> - **Tier 2** = 사용자 늘면 발목 잡힘
> - **Tier 3** = 성숙도
>
> 문서 작성일: 2026-05-21

## 형식 안내

각 항목은 다음 형식으로 추적합니다:

```
- [ ] T1-1. 항목명 — 해결일: -
  - 해결 PR/커밋: -
  - 비고: -
```

해결되면:

```
- [x] T1-1. 항목명 — 해결일: 2026-06-15
  - 해결 PR/커밋: feat: Flyway 도입 (#12)
  - 비고: V1__init.sql로 초기 스키마 동결
```

---

## Tier 1 — 배포 전 반드시 해결

### 1. DB 마이그레이션 도구 부재
- **현재 상태**: `spring.jpa.hibernate.ddl-auto: update`로 엔티티 자동 스키마 변경
- **왜 문제**: 컬럼명 변경 시 신컬럼 추가 + 구컬럼 잔존 → 누적 부채. 백업/롤백 불가. 운영 환경에서 데이터 손실 위험
- **해결 방향**: Flyway 또는 Liquibase 도입 → `V1__init.sql`, `V2__add_xxx.sql` 식 버전 관리. `ddl-auto: validate`로 전환

### 2. Admin 계정 코드 하드코딩
- **현재 상태**: `DataInitializer`가 `admin / admin123!!`을 자동 생성. 비번 코드 안에 명시
- **왜 문제**: 소스 공개 시 누구나 알 수 있는 백도어. GitHub에 이미 코드 올라가있음
- **해결 방향**: 환경변수 `INITIAL_ADMIN_PASSWORD` 받기 / 부재 시 랜덤 생성해서 로그에 1회만 출력 / 또는 setup 페이지로 첫 admin 수동 생성

### 3. 비밀번호 정책 미흡 + Brute Force 무방비
- **현재 상태**: `@Size(min=4)`만 검증. 로그인 실패 횟수 제한 없음
- **왜 문제**: 4자리 숫자 비번 가능. 자동화된 로그인 공격에 무방비
- **해결 방향**: 최소 8자 + 영문/숫자 혼합 검증 / Spring Security `AuthenticationFailureBadCredentialsEvent` + Bucket4j로 N회 실패 시 잠금 / (선택) HaveIBeenPwned API 연동

### 4. 시크릿 외부화
- **현재 상태**: `application-local.yaml`로만 분리. 파일 시스템에 평문 저장
- **왜 문제**: 서버 침해 시 그대로 노출. 다중 환경(dev/staging/prod) 관리 불편
- **해결 방향**: 운영 환경에선 Vault / AWS Secrets Manager / K8s Secret 연동. Spring Cloud Config 또는 Spring Boot 4의 `spring.config.import=optional:vault://...`

### 5. 글로벌 예외 처리 부재
- **현재 상태**: 컨트롤러마다 `try/catch` 흩어짐. `AccessDeniedException` 등은 그대로 500 응답
- **왜 문제**: 사용자에게 스택트레이스 노출 가능. 에러 응답 형식 비일관
- **해결 방향**: `@ControllerAdvice` 클래스 + `error/403.html`, `error/404.html`, `error/500.html`. 운영에선 `server.error.include-message=never`

### 6. 테스트 사실상 없음
- **현재 상태**: `UnitParserTest`만 존재. Service/Repository/Controller 테스트 0
- **왜 문제**: 회귀 발견이 사용자 불만으로만 가능. 리팩토링 무서움
- **해결 방향**: 최소 세트 — `@WebMvcTest`로 SecurityConfig 권한 룰 검증 / `@DataJpaTest`로 EntityGraph 동작 검증 / `IngredientService.fetchAndUpsert` (category 보존 upsert), `RecipeService.findMine` (소유자 체크), TTL refetch 분기 단위 테스트

---

## Tier 2 — 사용자 늘면 즉시 발목 잡힐 것들

### 7. 페이지네이션 없음
- **현재 상태**: 홈/검색 모두 하드코딩 N=20개. 그 이상 조회 수단 없음
- **왜 문제**: 레시피 10만개여도 20개만 보임. 사용자가 과거 레시피 발견 불가
- **해결 방향**: `Pageable` + `Page<Recipe>` 리턴, 템플릿에 prev/next + 페이지 번호. 또는 htmx 기반 무한 스크롤

### 8. Naver fetch가 사용자 요청을 블록
- **현재 상태**: `/ingredients?category=X` 호출 시 TTL 만료면 사용자가 Naver 응답까지 대기
- **왜 문제**: Naver 느려지거나 장애나면 사용자 화면 멈춤. 톰캣 스레드 고갈 가능
- **해결 방향**: 사용자 요청은 무조건 캐시(stale 허용)만 반환 / `@Scheduled` 백그라운드 잡이 TTL 만료 row를 주기 refetch / 또는 `@Async`로 non-blocking refetch 트리거

### 9. 외부 호출에 타임아웃/리트라이/서킷브레이커 없음
- **현재 상태**: `RestClient.create()` 디폴트 = 무한 대기 가능
- **왜 문제**: Naver 응답 지연 시 우리 스레드 다 잡힘 → 전체 서비스 다운
- **해결 방향**: connect/read timeout 명시 (예: 5초/10초) / Resilience4j로 retry (지수 backoff) + circuit breaker / Naver 일일 한도 도달 시 graceful fallback (캐시 데이터로 응답)

### 10. 캐싱 레이어 없음
- **현재 상태**: 모든 조회가 DB 직격
- **왜 문제**: 홈 페이지 트래픽이 곧 DB 부하. 같은 쿼리 반복
- **해결 방향**: Caffeine으로 메모리 캐시 (홈 목록 30초 TTL, 카테고리 목록 5분 등) → 규모 커지면 Redis로 확장

### 11. N+1 위험 잔존
- **현재 상태**: 일부 경로만 `@EntityGraph` 적용. `RecipeService.findMine`은 `getIngredients().size()`로 강제 초기화 중
- **왜 문제**: 트랜잭션 경계 밖에서 컬렉션 접근 시 LazyInitException 또는 N+1 발생
- **해결 방향**: 모든 컬렉션 접근 경로에 `JOIN FETCH` 또는 `@EntityGraph` 명시 / 개발 시 `spring.jpa.properties.hibernate.use_sql_comments=true`로 쿼리 가시화 / DataSource Proxy로 N+1 자동 감지

### 12. 동시 편집 충돌 방치
- **현재 상태**: A와 B가 동시에 같은 레시피 수정 → 나중 저장이 먼저 저장 덮어씀
- **왜 문제**: Lost update. 사용자 데이터 손실
- **해결 방향**: `@Version` 필드로 optimistic locking. 충돌 시 "다른 곳에서 수정됨" 메시지

### 13. Actuator / 모니터링 부재
- **현재 상태**: 프로세스 상태/메트릭 외부 가시성 0
- **왜 문제**: 장애 발생해도 알람 못 받음. 원인 분석 어려움
- **해결 방향**: `spring-boot-starter-actuator` → `/actuator/health`, `/actuator/metrics`, `/actuator/prometheus` 노출 / Prometheus + Grafana 구축 / 에러는 Sentry 연동

---

## Tier 3 — 서비스 성숙도

### 14. 회원/계정 부수 기능 부재
- **현재**: ID + 비번 로그인만
- **부족**: 이메일 인증 (가짜 계정 무제한 가입 가능), 비밀번호 재설정 (계정 잃으면 끝), 소셜 로그인 (이탈률 ↑)
- **해결**: 이메일 인증 토큰 발송 / 재설정 토큰 + 메일 / Spring Security OAuth2 client (Google, Kakao, Naver Login)

### 15. "허브"라면서 커뮤니티 요소 0
- **현재**: 레시피 공개 조회만
- **부족**: 좋아요, 댓글, 즐겨찾기, 팔로우, 작성자 프로필 페이지, 공유 링크
- **해결**: `Like`, `Comment`, `Favorite`, `Follow` 엔티티 + 페이지

### 16. 레시피 데이터 빈약
- **현재**: 이름, 인분, 재료(카테고리/양/단위)만
- **부족**: 요리 사진 (썸네일+여러 장), 조리 순서 스텝, 태그/카테고리 (한식/디저트 등), 조리시간/난이도
- **해결**: 이미지 업로드 (S3 또는 로컬 storage + presigned URL) / `RecipeStep` 엔티티 / `Tag` 다대다 / `Difficulty` enum

### 17. selectedIngredient 반쪽 구현
- **현재**: 엔티티 FK는 있는데 UI는 없음 (데드 코드)
- **부족**: 재료 행에서 "이 제품으로 고정" 선택 UI
- **해결**: 재료 입력 폼에 카테고리 옆 "특정 제품 선택" 모달/드롭다운

### 18. 카테고리 정합성 의존
- **현재**: admin이 자유 입력 ("밀가루" vs "박력분" vs "wheat flour")
- **부족**: 사용자가 "박력분" 입력해도 "밀가루" 카테고리 제품들과 매칭 안 됨
- **해결**: 정규화된 카테고리 마스터 테이블 / synonym/alias 매핑 / admin이 자유 입력 대신 선택지에서 선택

### 19. 가격 이력 추적 없음
- **현재**: Naver fetch가 upsert로 가격 덮어씀
- **부족**: "지난 주 밀가루가 얼마였지?" 알 수 없음. 원가 서비스인데 시계열 0
- **해결**: `IngredientPriceHistory` 테이블 (ingredient_id, price, fetched_at) 별도 적재

### 20. REST API 부재
- **현재**: 모든 게 Thymeleaf 서버 렌더링
- **부족**: 모바일 앱/SPA에서 사용 불가
- **해결**: `/api/v1/...` 별도 컨트롤러 + JSON + OpenAPI 문서 (springdoc-openapi)

### 21. i18n / 접근성
- **현재**: 한국어 하드코딩, 접근성 태그(aria-label 등) 없음
- **부족**: 글로벌 사용자 못 받음. 시각 장애 보조기기 사용 어려움
- **해결**: `messages_ko.properties`, `messages_en.properties` / Thymeleaf `#{key}` / WCAG 가이드라인 적용

### 22. CI/CD / Dockerfile 부재
- **현재**: 앱 자체 Dockerfile 없음 (MySQL만 docker-compose에 있음)
- **부족**: 자동 빌드/테스트/배포 파이프라인 0
- **해결**: 멀티스테이지 Dockerfile / GitHub Actions로 PR 시 테스트, main merge 시 이미지 빌드+레지스트리 push / 배포는 K8s 또는 단순 ssh+docker-compose

---

## 진행 상태 추적

체크박스 + 해결일 + 해결 PR/커밋 기록.

### Tier 1

- [x] T1-1. Flyway/Liquibase 도입 — 해결일: 2026-05-21
  - 해결 PR/커밋: feat: T1-1 — Flyway 도입 (V1 baseline + ddl-auto: validate)
  - 비고: `spring-boot-starter-flyway` + `flyway-mysql` 의존성. Spring Boot 4부터는 starter 필수(autoconfigure 모듈 분리됨, `flyway-core` 단독 X). 현재 운영 스키마를 `mysqldump --no-data`로 추출 → `V1__init_schema.sql`로 동결. `baseline-on-migrate: true` + `baseline-version: 1`로 기존 DB는 V1을 baseline으로만 마크(실행 X), 새 환경에서는 V1부터 실행. `ddl-auto: update` → `validate`로 전환(Hibernate가 엔티티 ↔ DB 일치만 검증). 로컬 검증 완료(`flyway_schema_history` 테이블 생성, V1 BASELINE 기록, 부팅 7.75s 정상).
- [x] T1-2. Admin 시드 시 환경변수/랜덤 비번 — 해결일: 2026-05-21
  - 해결 PR/커밋: feat: Stage A-1 — admin 비번 환경변수화 + 랜덤 fallback
  - 비고: `DataInitializer`에서 코드 하드코딩 제거. `app.admin.initial-password` (환경변수 `INITIAL_ADMIN_PASSWORD`)로 주입. 미설정 시 SecureRandom으로 16자 강한 랜덤 비번 생성 → WARN 로그로 1회 출력. 로컬 개발은 `application-local.yaml`에 고정 비번(`admin123!!`) 유지. 운영 EC2 배포 시 env로 전달.
- [x] T1-3. 비밀번호 정책 강화 + brute force 방어 — 해결일: 2026-05-21
  - 해결 PR/커밋: feat: T1-3 — 비밀번호 8자+영숫자 검증 + 메모리 카운터 잠금
  - 비고: **(1)** `SignupRequest.password`에 `@Size(min=8)` + `@Pattern("(?=.*[A-Za-z])(?=.*\\d).+")` — 4자 가능 → 8자 영숫자 필수. signup.html에 정책 안내. **(2)** `LoginAttemptService` (ConcurrentHashMap 기반) — username당 5회 실패 시 15분 잠금, 자동 해제. `AuthenticationEventListener`가 Spring Security의 `AuthenticationFailureBadCredentialsEvent`/`AuthenticationSuccessEvent`를 구독해 카운터 갱신. **(3)** `CustomUserDetailsService`가 `isBlocked(username)` 호출해서 `.accountLocked(true)`로 반환 → Spring이 `LockedException` 발생. `SecurityConfig.authenticationFailureHandler`가 `LockedException` 분기로 `/login?locked` redirect, login.html에 "15분 후 다시 시도" 메시지. **미해결**: IP 기반 차단(Bucket4j), captcha, 비번 재설정(T3-14).
- [x] T1-4. 시크릿 외부 저장소 연동 — 해결일: 2026-05-25 (옵션 A — 정책 강화, 외부 저장소 통합은 의도적 보류)
  - 해결 PR/커밋: feat: T1-4 시크릿 외부화 정책 강화 (운영 프로파일 + ProductionSecretsValidator + 운영 가이드)
  - 비고: 사용자 결정(옵션 A): 코드 최소 변경 + 부팅 검증 + 운영 가이드. AWS SM/Vault 실제 통합은 인스턴스 ≥ 2 또는 시크릿 ≥ 10 시점으로 보류. **(1)** `application-prod.yaml` 신규 — `SPRING_PROFILES_ACTIVE=prod`로 활성. show-sql/format_sql=false, thymeleaf.cache=true, mock-enabled 기본 false. **(2)** `ProductionSecretsValidator` (`@Profile("prod")` + `@PostConstruct`): DB 자격증명 디폴트(`coast`/`coastpass`) 거부 / `INITIAL_ADMIN_PASSWORD` 빈 채 거부(랜덤 비번 로그 노출 금지) / mock=false인데 NAVER_CLIENT_ID/SECRET 비면 거부. 위반 모두 모아 IllegalStateException(한 번에 표시). **(3)** `docker-compose.prod.yml`에 `SPRING_PROFILES_ACTIVE: prod` 자동 주입. **(4)** `.env.prod.example` 보강 — `[REQUIRED]` 마커 + `__REPLACE_WITH_OPENSSL_RAND_24__` placeholder + 안내. **(5)** `deployment.md § 9` 신설 — 파일 권한(chmod 600) / systemd EnvironmentFile / GitHub Actions Secrets / 시크릿 회전 절차 / 외부 저장소 통합 hook(AWS SM `spring.config.import=optional:aws-secretsmanager:` + Vault + SOPS). **테스트**: ProductionSecretsValidatorTest 12(happy/db/admin/naver 분기) = **총 119(이전 107 → +12)**. **의도적 비포함**: AWS SM/Vault 실 도입(과대투자), 시크릿 회전 자동화(분기 1회 빈도라 ROI 낮음), SOPS 암호화 git 저장(현재 .env는 EC2 위에서만 다루는 정책).
- [x] T1-5. 글로벌 ExceptionHandler + 에러 페이지 — 해결일: 2026-05-21
  - 해결 PR/커밋: feat: Task 6 마무리 (전역 예외 처리 + 에러 페이지)
  - 비고: `web/error/GlobalExceptionHandler`로 AccessDenied/NoResource/IllegalArgument/IllegalState/Generic 매핑. `templates/error/{403,404,500,error}.html` 커스텀 페이지. `server.error.include-message=never`로 운영 안전성 확보.
- [x] T1-6. 핵심 분기 통합 테스트 (Security, Repository, Service) — 해결일: 2026-05-22
  - 해결 PR/커밋: feat: T1-6 — 핵심 통합 테스트 4종 (Security, Repository, Service, LoginAttempt)
  - 비고: **(1)** 테스트 인프라 — H2(MySQL 호환 모드) testRuntimeOnly + `src/test/resources/application-test.yaml` (Flyway off, `ddl-auto: create-drop`, Naver Mock 강제, 별도 업로드 디렉토리). 운영(MySQL/Flyway) ↔ 테스트(H2/Hibernate) 분리. **(2)** `LoginAttemptServiceTest` — T1-3 정책(5회/15분) 자동 회귀 방지. 시간 의존성은 `Clock` 주입으로 분리해 `MutableClock`으로 시간 진행 시뮬레이션. 13 테스트. **(3)** `RecipeRepositoryTest` — `@DataJpaTest`로 EntityGraph 4개 메서드 검증 + Hibernate Statistics로 쿼리 카운트 측정해 N+1 회귀 방지. 4 테스트. **(4)** `RecipeServiceTest`/`IngredientServiceTest` — Mockito 단위 테스트. `findMine` 소유자 체크, `fetchAndUpsert` 카테고리 보존(invariant), 미존재/blank/skip 분기. 11 테스트. **(5)** `SecurityConfigTest` — `@SpringBootTest`+`MockMvc`로 익명/USER/ADMIN 3주체 × 공개/인증/관리자 경로 권한 룰 16 테스트. **총 56 테스트, 24초 통과.** **미해결**: 컨트롤러 단위 `@WebMvcTest`, `RecipeCostCalculator` 단위 테스트, Testcontainers 기반 MySQL 통합 테스트 — 후속.

### Tier 2

- [x] T2-7. 페이지네이션 — 해결일: 2026-05-25
  - 해결 PR/커밋: feat: T2-7 페이지네이션 (홈/검색/내 레시피 Pageable + prev/next + ±2 페이지 번호)
  - 비고: `RecipeRepository` 세 메서드(`findAllByOrderByCreatedAtDesc`, `findByNameContainingIgnoreCaseOrderByCreatedAtDesc`, `findByUserOrderByUpdatedAtDesc`)를 `List` → `Page<Recipe>` 반환으로 전환. `RecipeService`도 `Pageable` 인자/`Page` 반환. `HomeController` + `RecipeController.list`에 `?page&size` 쿼리 파라미터(기본 12, 상한 50). `home.html`/`recipes/list.html`에 페이지네이션 UI — prev/next + 현재 ±2 + 양 끝(0, total-1) + 사이는 `…`, q 파라미터 보존, 한 페이지뿐이면 hide. 페이지 메타("전체 N개 · K / total 페이지") 표시. `RecipeRepositoryTest` 기존 3개 갱신 + 페이지 메타데이터(totalElements/totalPages/hasNext/hasPrevious) 검증 케이스 1개 추가 — 총 65 테스트(이전 64 → +1). **미해결**: ToMany + Pageable 조합으로 Hibernate가 "in memory paging" 경고 띄울 수 있음. 페이지 12라 영향 미미하지만 root cause는 **T2-11**에서 two-step 쿼리(ID Page → entity fetch) 패턴으로 정리 예정.
- [x] T2-8. 비동기 / 스케줄러 기반 Naver refetch — 해결일: 2026-05-25
  - 해결 PR/커밋: feat: T2-8 비동기/스케줄러 Naver refetch (viewByCategory 블로킹 제거)
  - 비고: **(1)** `AsyncConfig` 신규 — `@EnableAsync` + `@EnableScheduling` + `naverRefetchExecutor` ThreadPoolTaskExecutor (core 2 / max 4 / queue 100 / DiscardPolicy). 외부 호출 스레드를 톰캣 요청 스레드와 분리. **(2)** `IngredientRefetchService` 신규: `triggerAsyncRefetch(category)` `@Async` + `ConcurrentHashMap<String, AtomicBoolean>` 카테고리별 락(중복 호출 skip, finally로 락 해제 — 예외 시에도). `scheduledRefresh()` `@Scheduled(fixedDelay=1h, initialDelay=1m)` + `findDistinctStaleCategoriesBefore` 쿼리로 stale 카테고리 일괄 trigger. `scheduledRefreshEnabled` `@Value` 플래그로 테스트 비활성. **(3)** `IngredientService.viewByCategory` `@Transactional(readOnly=true)`로 변경 + 블로킹 fetchAndUpsert 제거 → refetchService.triggerAsyncRefetch 호출만. 캐시(stale 허용) 즉시 반환. `@Lazy` 주입으로 양방향 의존(service ↔ refetch) 부팅 순서 보호. **(4)** `IngredientRepository.findDistinctStaleCategoriesBefore` JPQL 추가. **(5)** application.yaml에 `naver.api.scheduled-refresh-{enabled,interval-ms,initial-delay-ms}` 설정. test에서는 enabled=false. **테스트**: IngredientRefetchServiceTest 8(정상 + 락 + 동시성 + 예외 후 락 해제 + 비활성/일괄 trigger/empty) + IngredientServiceTest +4(viewByCategory 신규 동작) = **총 107(이전 95 → +12)**. **미해결**: 멀티 인스턴스 분산 락(ShedLock) — K8s 전환 시. 카테고리 우선순위/큐 영속화 — 카테고리 수십 개 이상 시 후속. 빈 결과 UI 안내(별도 UX 폴리시).
- [x] T2-9. 외부 호출 타임아웃/리트라이/서킷브레이커 — 해결일: 2026-05-23 (부분 해결)
  - 해결 PR/커밋: feat: T2-9 — Naver API 타임아웃 + Spring Retry + Recover fallback
  - 비고: **(1)** `RealNaverShoppingClient`의 `RestClient` 빌더가 `JdkClientHttpRequestFactory` 사용 — `connect-timeout-ms` 5초 / `read-timeout-ms` 10초 명시 (이전: 무한 대기). **(2)** Spring Retry 도입 (`spring-retry:2.0.12` + `spring-aspects`, Spring Boot BOM 미관리이라 버전 명시). `@EnableRetry` 활성, `search()`에 `@Retryable(retryFor={ResourceAccessException, HttpServerErrorException}, maxAttemptsExpression="${naver.api.max-attempts:3}", backoff=@Backoff(delayExpression="${naver.api.initial-backoff-ms:1000}", multiplier=2.0))` — 1s → 2s → 4s 지수 backoff. **(3)** `@Recover`로 모든 재시도 실패 시 빈 리스트 + WARN 로그 fallback — 사용자 UX 보호. 4xx는 retry 미트리거(즉시 실패). **(4)** `NaverApiProperties`에 timeout/retry 필드 추가 + 기본값 보정. **(5)** 통합 테스트(`RealNaverShoppingClientRetryTest`): JDK 내장 HttpServer로 stub Naver 서버 띄워 5xx 영속 실패 → 3회 호출 후 빈 리스트, 5xx 2회 → 3회째 성공, blank 키워드 즉시 빈 리스트 3 케이스 검증. 전체 59 테스트 29초 통과. **미해결**: 서킷브레이커(연속 실패 시 호출 차단)는 Resilience4j 도입 시 후속. 현재는 24h TTL 캐시 + Spring Retry 지수 backoff로 부하 일부 자제.
- [ ] T2-10. 캐싱 레이어 (Caffeine → Redis) — 해결일: -
  - 해결 PR/커밋: -
  - 비고: -
- [x] T2-11. N+1 점검 (전체 컬렉션 경로) — 해결일: 2026-05-25 (부분 해결)
  - 해결 PR/커밋: feat: T2-11 N+1 점검 후속 (two-step 쿼리 + findMine 강제 초기화 제거)
  - 비고: **(1) two-step 쿼리 패턴** — T2-7에서 도입한 `Page<Recipe>` 직접 반환은 ToMany(`ingredients`) + Pageable 조합으로 Hibernate "in-memory paging" 경고 유발. RecipeRepository를 ID-only `Page<Long>` 쿼리 3개 + IN 절 + EntityGraph + ORDER BY 보존 entity fetch 2개로 분리. RecipeService에 `assemblePage` 헬퍼로 세 페이징 메서드(findRecent/searchByName/findMyRecipes)가 공유. **(2) `findMine`의 `.getIngredients().size()` 강제 초기화 제거** — `findWithUserAndIngredientsById` EntityGraph 메서드로 명시화. **(3) `RecipeRepositoryTest` 5 → 8** — ID-only Page 쿼리 카운트(≤2), IN 절+EntityGraph 카운트(≤2), `findWithUserAndIngredientsById`(≤3) 회귀 검증. TS-3의 잔존 위험 해소(troubleshooting.md TS-3 갱신). **미해결**: DataSource Proxy 같은 개발 환경 자동 감지 도구는 별도 PR로 분리 (테스트 카운트 검증으로 회귀는 잡힘). 카드 뷰의 `${#lists.size(r.ingredients)}` 호출을 `RecipeCardView` projection DTO로 가볍게 만드는 건 추후 항목.
- [ ] T2-12. Optimistic locking (@Version) — 해결일: -
  - 해결 PR/커밋: -
  - 비고: -
- [ ] T2-13. Spring Boot Actuator + 모니터링 — 해결일: -
  - 해결 PR/커밋: -
  - 비고: -

### Tier 3

- [ ] T3-14. 이메일 인증 / 비번 재설정 / 소셜 로그인 — 해결일: -
  - 해결 PR/커밋: -
  - 비고: -
- [x] T3-15. 커뮤니티 기능 (좋아요/댓글/즐겨찾기/팔로우) — 해결일: 2026-05-21 (부분 해결)
  - 해결 PR/커밋: feat: Task 7 — 좋아요 + 댓글(1단계 대댓글) + 레시피 이미지
  - 비고: **좋아요(toggle)** 및 **댓글 + 1단계 대댓글(작성/삭제)** 완성. `RecipeLike` 엔티티 + `(recipe_id, user_id)` unique 제약, `Comment` 엔티티 + self-FK `parent`, `CommentService.listForRecipe`에서 1쿼리 fetch 후 그루핑. 댓글 삭제는 작성자 본인 + ADMIN. **즐겨찾기/팔로우/작성자 프로필 페이지**는 미해결 — 후속 항목으로 남김.
- [x] T3-16. 레시피 이미지 + 조리 스텝 + 태그 — 해결일: 2026-05-21 (부분 해결)
  - 해결 PR/커밋: feat: Task 7 — 좋아요 + 댓글(1단계 대댓글) + 레시피 이미지
  - 비고: **이미지 1장(썸네일)** 업로드 완성. `Recipe.imageUrl` 컬럼, `ImageStorageService`/`LocalImageStorageService` (로컬 `./uploads/recipes/`), `WebMvcConfig`로 `/uploads/**` 정적 서빙, multipart 검증(확장자 jpg/jpeg/png/webp, 5MB), edit 폼에서 교체/삭제 가능. **조리 스텝, 태그/난이도, 갤러리(여러 장)**는 미해결.
- [x] T3-17. selectedIngredient UI 완성 — 해결일: 2026-05-23
  - 해결 PR/커밋: feat: T3-17 — selectedIngredient UI (고정 제품 드롭다운 + 카테고리/단위 검증)
  - 비고: 폼 GET 시점에 `IngredientService.findAllVisible()`을 `LinkedHashMap<카테고리, List<Ingredient>>`로 그루핑해 모델 주입(`ingredientGroups`). `form.html` 재료 행에 다섯 번째 칼럼 "고정 제품 (선택)" 추가 — `<select>` 안에 `자동 (정책 사용)` + `<optgroup>` 카테고리별 옵션(라벨: `title — mallName (pricePerGram원/g|ml)`). JS 없음(Open Q #24) 정책 준수. `RecipeIngredientForm.selectedIngredientId` 필드 추가, `from()`에서 기존 selectedIngredient.id 복원. `RecipeService.applyIngredients`에 `resolveSelectedIngredient` 헬퍼 — 선택한 제품이 미존재/카테고리 미부여/카테고리 불일치(대소문자 무시)/단위 불일치 시 IllegalArgumentException으로 저장 거부(사용자 의도 보존, 옵션 1 — 자동 덮어쓰기·무시 X). `detail.html` 재료 테이블에 "고정 제품" 칼럼 추가(selectedIngredient null이면 `— (자동)`). `RecipeRepository.findWithDetailsById` EntityGraph에 `ingredients.selectedIngredient` 추가 — open-in-view: false 환경에서 LazyInitializationException 방지, RecipeRepositoryTest의 쿼리 카운트 ≤3 임계값은 그대로(Hibernate가 LEFT JOIN으로 묶음). RecipeServiceTest +5 — set / 단위 불일치 / 카테고리 불일치 / null 유지 + ingredientRepository 미호출 / 미존재 ID. **총 64 테스트(이전 59 → +5)**.
- [x] T3-18. 카테고리 정규화 (마스터 테이블 or synonym) — 해결일: 2026-05-25 (마스터 + alias 둘 다 해결, FK/삭제 UI는 의도적 비포함)
  - 해결 PR/커밋: feat: T3-18 카테고리 마스터 + datalist 자동완성 / feat: T3-18.2 카테고리 alias/synonym 매핑
  - 비고: **마스터 (T3-18, 2026-05-25)**: **(1)** Flyway `V2__category_master.sql` — `categories(id, name unique, created_at)` + 기존 `ingredients.category` distinct 값 시드(NOT IN 패턴). **(2)** `domain/category/`: `Category` 엔티티 / `CategoryRepository` / `CategoryService`(findAllNames, ensureExists 멱등). **(3)** `IngredientService.updateCategory`에서 `categoryService.ensureExists` 자동 호출. **(4)** `admin/ingredients/edit.html` + `recipes/form.html`에 HTML5 `<datalist>` 자동완성. **(5)** `Ingredient.category`는 String 유지(FK 전환 의도적 X). **alias/synonym (T3-18.2, 2026-05-25)**: **(6)** Flyway `V3__category_aliases.sql` — `category_aliases(id, alias unique, canonical_category_id FK ON DELETE CASCADE, created_at)`. **(7)** `CategoryAlias` 엔티티 + `CategoryAliasRepository`(`@EntityGraph(canonical)`) + `CategoryAliasService`(resolve/add/delete). **(8)** 정책: 옵션 A — 매칭 단계에서만 정규화, 저장은 사용자 의도 그대로 (T3-17 정책 일관). **(9)** `RecipeCostCalculator.calculate`에서 `findByCategoryAndUnit` 직전 resolve 적용. **(10)** `RecipeService.resolveSelectedIngredient`에서 양쪽 resolve 후 비교 (한 쪽이 alias여도 풀어서 같으면 통과). **(11)** Admin UI `/admin/category-aliases` (목록+추가/삭제, JS 없음 + datalist). **테스트**: CategoryAliasServiceTest 14 + RecipeCostCalculatorTest 3 + RecipeServiceTest +2 = **총 95 테스트(이전 76 → +19)**. **의도적 비포함**: 저장 시 정규화(옵션 B 거부), alias 체인(구조적 차단), bulk import, `Ingredient.category` FK 전환(별도 마이그레이션 PR), 카테고리 삭제 UI(category_aliases CASCADE는 OK이나 ingredients.category는 String이라 정합성 어려움).
- [ ] T3-19. 가격 이력 (IngredientPriceHistory) — 해결일: -
  - 해결 PR/커밋: -
  - 비고: -
- [ ] T3-20. REST API + OpenAPI 문서 — 해결일: -
  - 해결 PR/커밋: -
  - 비고: -
- [ ] T3-21. i18n + 접근성 — 해결일: -
  - 해결 PR/커밋: -
  - 비고: -
- [x] T3-22. Dockerfile + CI/CD 파이프라인 — 해결일: 2026-05-21 + 후속 2026-05-23
  - 해결 PR/커밋: feat: Stage A-2 — 운영용 Dockerfile + docker-compose.prod / feat: T3-22 후속 — GitHub Actions CI(test + GHCR push)
  - 비고: **Dockerfile** (2026-05-21) — 멀티스테이지(JDK 25 builder → JRE 25 runtime, 비루트 app, JAVA_OPTS env). **`docker-compose.prod.yml`** — 앱 + MySQL 한 머신, 외부 포트는 앱 80만, 볼륨(`coast-uploads`, `coast-mysql-data`), healthcheck. **`.env.prod.example`** + `.dockerignore` 추가, `.env.prod`는 .gitignore. **CI/CD (2026-05-23)** — `.github/workflows/ci.yml`: `test` job (PR + main push, JDK 25 Temurin + Gradle cache + `./gradlew test --no-daemon --stacktrace`, 15분 timeout, test report 아티팩트 항상 업로드), `build-and-push` job (main push 전용, `needs: test`, Buildx + GHCR 로그인 + `docker/build-push-action@v6`, `latest` + `sha-<short>` 두 태그, GHA 빌드 캐시). 권한 최소화(`contents: read` 기본, push job만 `packages: write`). GHCR owner는 `tr` 로 소문자 정규화해 `Goospel` 같은 대문자 owner에도 안전. **미해결**: 배포 자동화(EC2 ssh + docker compose pull) — 실 배포 시. Dependabot/CodeQL 분리 워크플로우 — 후속.

---

## 한 가지만 먼저 고친다면

**`@ControllerAdvice` + Flyway + 핵심 통합 테스트 3종 세트** — 이게 없으면 다음 기능 추가할 때마다 "이거 깨지면 어떡하지" 공포가 누적되고 결국 손 못 대는 코드가 됨. 다른 항목은 배포 직전에 한 번에 강화해도 늦지 않지만, 이 셋은 **개발 사이클 자체의 안전망**.
