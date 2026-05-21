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

- [ ] T1-1. Flyway/Liquibase 도입 — 해결일: -
  - 해결 PR/커밋: -
  - 비고: -
- [ ] T1-2. Admin 시드 시 환경변수/랜덤 비번 — 해결일: -
  - 해결 PR/커밋: -
  - 비고: -
- [ ] T1-3. 비밀번호 정책 강화 + brute force 방어 — 해결일: -
  - 해결 PR/커밋: -
  - 비고: -
- [ ] T1-4. 시크릿 외부 저장소 연동 — 해결일: -
  - 해결 PR/커밋: -
  - 비고: -
- [ ] T1-5. 글로벌 ExceptionHandler + 에러 페이지 — 해결일: -
  - 해결 PR/커밋: -
  - 비고: -
- [ ] T1-6. 핵심 분기 통합 테스트 (Security, Repository, Service) — 해결일: -
  - 해결 PR/커밋: -
  - 비고: -

### Tier 2

- [ ] T2-7. 페이지네이션 — 해결일: -
  - 해결 PR/커밋: -
  - 비고: -
- [ ] T2-8. 비동기 / 스케줄러 기반 Naver refetch — 해결일: -
  - 해결 PR/커밋: -
  - 비고: -
- [ ] T2-9. 외부 호출 타임아웃/리트라이/서킷브레이커 — 해결일: -
  - 해결 PR/커밋: -
  - 비고: -
- [ ] T2-10. 캐싱 레이어 (Caffeine → Redis) — 해결일: -
  - 해결 PR/커밋: -
  - 비고: -
- [ ] T2-11. N+1 점검 (전체 컬렉션 경로) — 해결일: -
  - 해결 PR/커밋: -
  - 비고: -
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
- [ ] T3-15. 커뮤니티 기능 (좋아요/댓글/즐겨찾기/팔로우) — 해결일: -
  - 해결 PR/커밋: -
  - 비고: -
- [ ] T3-16. 레시피 이미지 + 조리 스텝 + 태그 — 해결일: -
  - 해결 PR/커밋: -
  - 비고: -
- [ ] T3-17. selectedIngredient UI 완성 — 해결일: -
  - 해결 PR/커밋: -
  - 비고: -
- [ ] T3-18. 카테고리 정규화 (마스터 테이블 or synonym) — 해결일: -
  - 해결 PR/커밋: -
  - 비고: -
- [ ] T3-19. 가격 이력 (IngredientPriceHistory) — 해결일: -
  - 해결 PR/커밋: -
  - 비고: -
- [ ] T3-20. REST API + OpenAPI 문서 — 해결일: -
  - 해결 PR/커밋: -
  - 비고: -
- [ ] T3-21. i18n + 접근성 — 해결일: -
  - 해결 PR/커밋: -
  - 비고: -
- [ ] T3-22. Dockerfile + CI/CD 파이프라인 — 해결일: -
  - 해결 PR/커밋: -
  - 비고: -

---

## 한 가지만 먼저 고친다면

**`@ControllerAdvice` + Flyway + 핵심 통합 테스트 3종 세트** — 이게 없으면 다음 기능 추가할 때마다 "이거 깨지면 어떡하지" 공포가 누적되고 결국 손 못 대는 코드가 됨. 다른 항목은 배포 직전에 한 번에 강화해도 늦지 않지만, 이 셋은 **개발 사이클 자체의 안전망**.
