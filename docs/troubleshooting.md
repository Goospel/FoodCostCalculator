# 트러블슈팅 기록

> 개발 중 만난 함정과 해결 과정. 비슷한 일이 또 일어나면 여기 먼저 확인.
>
> 형식: **증상 → 진단 → 해결 → 교훈**

---

## TS-1. Spring Boot 4 — `flyway-core` 단독 추가하면 자동설정이 안 됨

**날짜**: 2026-05-21 / 관련 작업: T1-1 Flyway 도입

### 증상
- `build.gradle`에 `org.flywaydb:flyway-core` + `org.flywaydb:flyway-mysql` 추가
- `application.yaml`에 `spring.flyway.*` 설정
- `src/main/resources/db/migration/V1__init_schema.sql` 작성
- 부팅해도 **Flyway 로그가 한 줄도 안 나옴**
- `flyway_schema_history` 테이블이 만들어지지 않음
- `ddl-auto: validate`는 그냥 통과 (기존 스키마가 이미 있으니까)

겉으로는 멀쩡해 보이는데 마이그레이션이 작동 안 하는 가장 무서운 상태.

### 진단 명령어 (이걸로 30분만에 풀었음)
```bash
# 1) 의존성은 들어가 있나? → 들어감 (flyway-core:11.14.1)
./gradlew dependencies --configuration runtimeClasspath | grep -i flyway

# 2) Spring Boot autoconfigure jar 안에 FlywayAutoConfiguration이 있나?
AUTOCONF=$(find ~/.gradle/caches -name "spring-boot-autoconfigure-4.0.6.jar" | head -1)
jar tf "$AUTOCONF" | grep -i flyway
unzip -p "$AUTOCONF" "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports" | grep -i flyway
# → 둘 다 비어있음. 자동설정이 아예 빠져있다는 신호.
```

### 원인 (한 줄)
**Spring Boot 4부터 autoconfigure가 라이브러리별 모듈로 분리됐다.** `spring-boot-autoconfigure` 메인 jar에는 더 이상 Flyway/Redis/Quartz 같은 인프라 모듈의 자동설정이 없다. 별도 `spring-boot-{tech}` 모듈 또는 `spring-boot-starter-{tech}` 추가가 필수.

### 해결
```gradle
// 잘못된 방식 (Spring Boot 3까지는 통했지만 4부터는 X)
implementation 'org.flywaydb:flyway-core'

// 올바른 방식 — starter가 spring-boot-flyway autoconfigure 모듈을 끌어옴
implementation 'org.springframework.boot:spring-boot-starter-flyway'
implementation 'org.flywaydb:flyway-mysql'   // DB 어댑터는 별도
```

확인:
```bash
./gradlew dependencies --configuration runtimeClasspath | grep -i flyway
# → spring-boot-starter-flyway → spring-boot-flyway:4.0.6 → flyway-core:11.14.1
#   spring-boot-flyway 가 autoconfigure 모듈
```

부팅 시 다음 로그가 보여야 정상:
```
o.f.c.i.s.JdbcTableSchemaHistory  : Schema history table ... does not exist yet
o.f.core.internal.command.DbValidate : Successfully validated N migration
o.f.core.internal.command.DbBaseline : Successfully baselined schema with version: X
```

### 교훈 — 다른 라이브러리에도 같은 패턴 적용 가능성
**Spring Boot 4에서 `xxx-core` 단독 추가했는데 자동설정이 안 도는 것 같으면, 95% 이걸 의심.**

같은 함정이 예상되는 라이브러리:
- Redis (`spring-boot-starter-data-redis` 필요)
- Quartz (`spring-boot-starter-quartz`)
- Batch (`spring-boot-starter-batch`)
- Actuator (`spring-boot-starter-actuator`)
- Mail (`spring-boot-starter-mail`)
- Cache (`spring-boot-starter-cache`)
- Liquibase (`spring-boot-starter-liquibase`)

**범용 진단 체크리스트** (의존성 추가했는데 동작이 이상할 때):
1. `./gradlew dependencies --configuration runtimeClasspath | grep -i <라이브러리>` — 의존성 들어가 있나?
2. 메인 jar에 AutoConfiguration이 들어있나?
   ```
   find ~/.gradle/caches -name "spring-boot-autoconfigure-4.0.*.jar" | head -1 | \
     xargs -I {} unzip -p {} "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports" | \
     grep -i <라이브러리>
   ```
   비어있으면 → 별도 starter 필요
3. `https://search.maven.org/search?q=g:org.springframework.boot%20AND%20a:spring-boot-starter-<tech>` 에서 starter 존재 여부 확인

참고 자료:
- [Spring Boot 4 Modularization (Dan Vega)](https://www.danvega.dev/blog/spring-boot-4-modularization)
- [Modularizing Spring Boot (Spring Blog)](https://spring.io/blog/2025/10/28/modularizing-spring-boot/)
- [Add spring-boot-starter-flyway recipe (OpenRewrite)](https://docs.openrewrite.org/recipes/java/spring/boot4/addspringbootstarterflyway)

---

## TS-2. Naver Shopping API 401 — "Scope Status Invalid"

**날짜**: 2026-05-21 / 관련 작업: Task 4.5 Naver API 키 적용

### 증상
- `application-local.yaml`에 발급받은 Client ID/Secret 정확히 입력
- `mock-enabled: false`로 토글
- admin이 fetch 시도하면 **401 Unauthorized** 응답
- 응답 body에 `"errorCode":"024","errorMessage":"Scope Status Invalid"`

### 진단
- ID/Secret 오타 의심 → 다시 확인, 정확함
- 발급 직후 활성화 지연 의심 → 시간 지나도 그대로
- **Naver 개발자센터에서 "검색 API" 권한이 활성화 안 됨** 발견

### 해결
1. https://developers.naver.com/ 로그인
2. 내 애플리케이션 → 해당 앱 클릭
3. **사용 API 추가** → "검색" 선택 → 저장
4. 잠깐 기다린 후 다시 fetch

### 교훈
- Naver API 키 자체는 발급되지만 **API별 스코프는 별도 선택**해야 한다
- 401인데 메시지가 "Invalid credentials"가 아니라 "Scope Status Invalid"면 권한 문제
- 다른 Naver 오픈 API(블로그, 뉴스, 책 등)도 같은 패턴 — 처음 쓰는 API는 권한 추가 단계 필수

---

## TS-3. `/recipes` 진입 시 500 (LazyInitializationException)

**날짜**: 2026-05-21 / 관련 작업: Task 4 Recipe 도메인

### 증상
- 사용자 로그인 후 레시피 생성까지 정상
- 다시 `/recipes`로 돌아오면 **500 에러**
- 로그: `LazyInitializationException: could not initialize proxy [...RecipeIngredient] - no Session`

### 진단
- `application.yaml`에 `spring.jpa.open-in-view: false` 설정 (운영 안전성)
- `RecipeService.findMyRecipes`는 `@Transactional(readOnly = true)` 내에서 Repository 호출
- 그런데 트랜잭션 끝난 뒤 **Thymeleaf 템플릿에서 `${r.ingredients}` 접근** → LAZY 컬렉션을 초기화하려는데 세션이 이미 닫힘 → 폭발

### 해결
- `RecipeRepository.findByUserOrderByUpdatedAtDesc`에 `@EntityGraph(attributePaths = {"ingredients"})` 추가
- 같은 패턴으로 `findAllByOrderByCreatedAtDesc`, `findByNameContainingIgnoreCase...`, `findWithDetailsById`에도 EntityGraph 적용

### 교훈
- **`open-in-view: false`는 안전하지만 비용이 있다** — 모든 컬렉션 접근 경로에 EntityGraph 또는 JOIN FETCH 명시 필요
- 증상이 컨트롤러에서 정상이고 템플릿 렌더링 중에만 나면 100% LAZY 초기화 문제
- ~~잔존 위험은 `improvements.md` T2-11에서 추적~~ → **T2-11에서 정리 완료 (2026-05-25)**. ToMany + Pageable 조합은 **two-step 쿼리(ID Page → IN 절 + EntityGraph)** 패턴으로 in-memory paging 회피. `findMine`의 `.getIngredients().size()` 강제 초기화도 `findWithUserAndIngredientsById` EntityGraph 메서드로 대체. 자세한 건 plan.md T2-11.

---

## TS-4. `ddl-auto: update` 의 누적 부채

**날짜**: 2026-05-21 / 관련 작업: T1-1 Flyway 도입

### 증상
이 자체로는 즉시 깨지지 않는다 — 진짜 위험은 운영 진입 후에 드러난다.

### 위험 사례
- 엔티티에서 컬럼 이름 변경 → Hibernate는 신 컬럼 ADD만 함, 구 컬럼 DROP 안 함 → 데이터 둘로 쪼개짐
- nullable=false 컬럼 추가 → 기존 row 채울 default 없으면 ALTER 실패 → 부팅 안 됨
- 백업/롤백 불가 — Hibernate가 만든 DDL을 추적할 방법 없음

### 해결 (T1-1로 종결)
- Flyway 도입 + `V1__init_schema.sql`로 스키마 동결
- `ddl-auto: validate`로 전환 → Hibernate는 검증만, 변경은 Flyway가 담당
- 향후 스키마 변경은 `V2__xxx.sql`, `V3__xxx.sql` 추가

### 교훈
- **개발 초기에는 `update`로 빠르게, 첫 사용자 받기 직전 반드시 Flyway로 동결**
- 데이터 적을 때 동결하면 baseline 충돌이 거의 없음 — 늦을수록 손해
- 동결 절차: mysqldump → V1 작성 → `baseline-on-migrate: true` + `baseline-version: 1`

---

## TS-5. `git add` 가 docs/ 하위에서만 동작

**날짜**: 2026-05-21

### 증상
- 변경 사항이 명백한데 `git status`에 일부 파일만 staged
- `git add .` 했는데도 누락

### 원인
- `docs/` 하위에서 `git add .` 실행 → 그 디렉토리 안의 변경만 stage
- `.` 은 현재 디렉토리 기준

### 해결
- 프로젝트 루트로 이동 후 `git add .`
- 또는 어디서든 `git add -A` (전체 작업트리)

### 교훈
- `git add .` 와 `git add -A` 의 동작 차이 기억하기
- 깊은 디렉토리에서 작업 후엔 `git add -A` 가 안전

---

## 공통: 진단 명령어 모음

```bash
# 8080 포트 점유 프로세스 (Windows)
netstat -ano | grep ":8080" | grep LISTENING
taskkill //F //PID <PID>

# Gradle 의존성 트리에서 특정 라이브러리
./gradlew dependencies --configuration runtimeClasspath | grep -i <name>

# Spring Boot autoconfigure jar 안의 imports 파일
find ~/.gradle/caches -name "spring-boot-autoconfigure-4.0.*.jar" | head -1 | \
  xargs -I {} unzip -p {} "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"

# MySQL Docker 컨테이너에 쉘 접속
docker exec -it coast-calculator-mysql mysql -uroot -prootpass coast_calculator

# Flyway 적용 상태
docker exec coast-calculator-mysql mysql -uroot -prootpass coast_calculator \
  -e "SELECT installed_rank, version, description, type, success FROM flyway_schema_history;"

# 앱 부팅 로그를 백그라운드로 띄우고 특정 메시지 대기
./gradlew bootRun --args='--spring.profiles.active=local' &
until grep -qE "Started CoastCalculatorApplication|APPLICATION FAILED" app.log; do sleep 2; done
```

---

## TS-6. `spring-retry`는 Spring Boot BOM 미관리 — 버전 명시 필요

**날짜**: 2026-05-23 / 관련 작업: T2-9 외부 호출 안정성

### 증상
- `build.gradle`에 `implementation 'org.springframework.retry:spring-retry'` 만 추가
- 컴파일 시 에러:
  ```
  Could not find org.springframework.retry:spring-retry:.
  Required by:
      root project 'coastCalculator'
  ```

### 진단
Spring Boot 4 의 `spring-boot-dependencies` BOM은 `spring-aspects`는 관리하지만 `spring-retry`는 관리 대상이 아님. 그래서 버전 정보 없이 적었더니 Gradle이 어떤 버전을 가져올지 결정 못 함.

```bash
# 다른 spring 모듈은 BOM 관리 (버전 명시 없이도 OK)
find ~/.gradle/caches -path "*spring-aspects*" -name "*.jar" | head -2
# 출력: spring-aspects-7.0.7.jar  ← Spring Framework 7.x 따라옴

# spring-retry는 별도 프로젝트라 명시 필요
```

### 해결
버전을 직접 명시:
```gradle
implementation 'org.springframework.retry:spring-retry:2.0.12'
implementation 'org.springframework:spring-aspects'    // 이건 BOM 관리
```

추가로 `@EnableRetry`를 적용한 `@Configuration` 클래스 필요. 어노테이션만 추가해도 Spring Boot가 알아서 등록해주는 게 아님.

### 교훈
- Spring Boot **BOM이 모든 Spring 모듈을 관리하지는 않음**. `spring-batch`, `spring-retry`, `spring-cloud-*` 등은 별도 프로젝트라 버전 명시가 필요할 수 있음
- 의심되면 `./gradlew dependencies --configuration compileClasspath | grep <이름>`로 확인
- `spring-boot-dependencies` BOM의 관리 목록은 [공식 문서](https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/#appendix.dependency-versions)에서 확인 가능
- "Could not find ...:<lib>:." (버전이 빈 콜론) → BOM 미관리 라이브러리 신호

---

## TS-7. `@Retryable` SpEL — `@ConfigurationPropertiesScan` 빈 이름은 SpEL에서 다루기 까다로움

**날짜**: 2026-05-23 / 관련 작업: T2-9

### 증상
- 설정값(`naver.api.max-attempts`)으로 `@Retryable.maxAttempts`를 동적으로 주입하려고 함
- 처음 시도: `maxAttemptsExpression = "#{@naverApiProperties.maxAttempts}"`
- `@ConfigurationPropertiesScan`으로 등록된 record의 bean name은 `<prefix>-<FQCN>` 형태 (예: `naver.api-com.goosepl.coastCalculator.config.NaverApiProperties`) → SpEL에서 `@`로 참조 시 점/하이픈 때문에 깨짐

### 해결
**SpEL 빈 참조 대신 프로퍼티 플레이스홀더 `${}`를 그대로 쓰기**:
```java
@Retryable(
    retryFor = {...},
    maxAttemptsExpression = "${naver.api.max-attempts:3}",
    backoff = @Backoff(
        delayExpression = "${naver.api.initial-backoff-ms:1000}",
        multiplier = 2.0
    )
)
```

`maxAttemptsExpression`은 SpEL이지만 `${}` placeholder를 그대로 받아서 환경값으로 치환됨. 기본값(`:3`)도 명시 가능. record bean을 거치지 않아서 더 단순.

### 교훈
- 설정값을 어노테이션 SpEL에서 쓸 때 첫 번째 선택지는 **`${...}` 플레이스홀더** — 빈 이름이 복잡하면 더 그렇다
- `#{@beanName.field}`는 빈 이름이 단순할 때만 추천
- `@Retryable`/`@Scheduled`/`@Cacheable` 등 어노테이션의 `*Expression` 속성은 모두 같은 패턴

---

## TS-8. `@SpringBootTest` 통합 테스트에서 외부 HTTP 의존성 — JDK 내장 `HttpServer` + `@DynamicPropertySource`

**날짜**: 2026-05-23 / 관련 작업: T2-9 retry 통합 테스트

### 증상
- `@Retryable` 어노테이션은 Spring AOP 프록시로 동작 → 단위 테스트로 검증 불가, 컨테이너 안에서만 활성
- 외부 API(Naver) 의존이라 WireMock 등 추가 의존성 도입은 부담

### 해결
**JDK 내장 `com.sun.net.httpserver.HttpServer`** 로 가벼운 stub 서버 + **`@DynamicPropertySource`** 로 포트 동적 주입.

```java
@SpringBootTest(properties = { "naver.api.mock-enabled=false", ... })
class RealNaverShoppingClientRetryTest {
    static HttpServer stubServer;
    static int stubPort;

    @DynamicPropertySource
    static void registerBaseUrl(DynamicPropertyRegistry registry) throws IOException {
        if (stubServer == null) {
            stubServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);  // 0 = 임의 포트
            stubPort = stubServer.getAddress().getPort();
            stubServer.createContext("/test/shop", exchange -> { ... });
            stubServer.start();
        }
        registry.add("naver.api.base-url", () -> "http://127.0.0.1:" + stubPort + "/test/shop");
    }
}
```

핵심 포인트:
- `InetSocketAddress("127.0.0.1", 0)` — 포트 0 = OS 할당 → `getAddress().getPort()`로 실제 포트 획득
- `@DynamicPropertySource`는 컨텍스트 로딩 **이전**에 호출 → 빈 생성 시점에 환경값 반영됨
- 응답 JSON은 record 필드를 **빠짐없이** 채워야 (Jackson 디시리얼라이즈 실패 → `RestClientException` → retry 미트리거 + Recover 진입으로 오인됨)
- AtomicInteger 카운터 + volatile 모드 플래그로 시나리오 전환

### 교훈
- 외부 HTTP 의존성 테스트는 WireMock 없이도 가능 — JDK `HttpServer`로 충분
- `@DynamicPropertySource`는 동적 인프라(랜덤 포트, Testcontainers IP) 주입의 표준 방법
- record 기반 응답 DTO 테스트할 때 stub 응답은 **모든 필드 포함**해야 — 누락 필드가 deserialization 실패 일으키면 디버깅 어렵다

---

## TS-9. Windows에서 `bootRun` 8080 포트 bind 실패 — listening 프로세스가 없는데도

**날짜**: 2026-05-23 / 관련 작업: T3-17 부팅 검증

### 증상
- `./gradlew bootRun` 실행 시 Tomcat이 `8080 already in use` 비슷한 에러로 시작 실패
- 그런데 `Get-NetTCPConnection -LocalPort 8080 -State Listen` 으로 보면 **listening 프로세스가 없음**
- `netstat -ano | findstr :8080` 도 비어있음

겉으로는 비어있는데 bind 실패 — 가장 헷갈리는 상태.

### 진단
```powershell
# 가상화 컴포넌트가 예약한 동적 포트 범위 확인
netsh int ipv4 show excludedportrange protocol=tcp
```
출력에서 **8080이 포함된 범위**가 있는지 확인:
```
Start Port    End Port
----------    --------
      8060        8159   ← 8080 여기 들어감
      9481        9580
      ...
```

### 원인 (한 줄)
**Hyper-V / WSL2 / Docker Desktop이 부팅 시 동적 포트 범위를 무작위로 예약**한다. 예약된 범위에 8080이 걸리면 일반 프로세스가 bind 불가 (listening 없이도). Windows 10 1809 이후 `winnat`/`hns` 서비스가 이 동작을 자주 함.

### 해결
**로컬 개발만 영향받는 방식 — `application-local.yaml`에 다른 포트 명시**:
```yaml
# 예약 범위(8060-8159) 밖이고 다른 충돌 없는 포트
server:
  port: 8181
```

`application-local.yaml`은 `.gitignore`에 들어가 있어 Docker/CI/운영(`application.yaml` 기본 8080)에 영향 없음.

선택할 포트 검증 스니펫:
```powershell
$ranges = @(); netsh int ipv4 show excludedportrange protocol=tcp `
  | Select-String '^\s*(\d+)\s+(\d+)' `
  | ForEach-Object { $ranges += ,@([int]$_.Matches[0].Groups[1].Value, [int]$_.Matches[0].Groups[2].Value) }
foreach ($p in 8181, 8160, 9000, 9090) {
  $inExcl = $false; foreach ($r in $ranges) { if ($p -ge $r[0] -and $p -le $r[1]) { $inExcl = $true; break } }
  $listening = Get-NetTCPConnection -LocalPort $p -State Listen -ErrorAction SilentlyContinue
  $status = if ($inExcl) { "EXCLUDED" } elseif ($listening) { "IN USE" } else { "FREE" }
  "Port $p`: $status"
}
```

근본 해결(권하지 않음): 관리자 PowerShell에서 `netsh int ipv4 set dynamic tcp start=49152 num=16384` + 재부팅. 다른 가상화 도구 동작에 영향 줄 수 있어 부작용 큼.

### 교훈
- Windows 개발 환경에서 "포트 비어있는데 bind 실패" = 거의 100% **excluded port range** 의심
- listening 안 잡힌다고 안 쓰는 게 아니다 — `netsh ... excludedportrange`로 따로 확인
- 8080은 8060-8159 범위에 자주 잡히는 단골. 로컬 개발은 8181/9000/9090 같은 범위 밖 포트 권장
- yaml 분리 정책 덕에 1줄 추가로 끝남 — 운영 yaml 안 건드림

---

## TS-10. GHCR — 첫 push는 됐는데 두 번째 push가 `denied: denied` 로 실패

**날짜**: 2026-05-23 / 관련 작업: T3-17 PR 머지 후 build-and-push job 실패

### 증상
- `.github/workflows/ci.yml`의 `build-and-push` job, **첫 번째 머지(PR #1)에선 GHCR push 성공**
- 같은 워크플로우, 같은 권한 설정인데 **두 번째 머지(PR #2)부터 Login to GHCR 단계에서 실패**
- 로그:
  ```
  Logging into ghcr.io...
  Error response from daemon: Get "https://ghcr.io/v2/": denied: denied
  ```
- 권한은 분명 `packages: write` 부여, `username: ${{ github.actor }}`, `password: ${{ secrets.GITHUB_TOKEN }}`

### 원인 (한 줄)
**첫 번째 push로 생성된 user-level 패키지가 source repository와 자동 link되지 않은 상태**. 첫 push는 GITHUB_TOKEN에 자동 허용되지만, 두 번째 이후는 패키지 자체의 access policy로 결정 → policy가 비어있으면 같은 GITHUB_TOKEN도 거부.

### 해결 (한 번만 하면 됨)
1. **패키지 페이지** 접근: https://github.com/&lt;owner&gt;?tab=packages → 해당 패키지 클릭
2. **Package settings** (우측 사이드바)
3. **Manage Actions access** 섹션 → "Add Repository" 클릭 → source repo 선택 → **Role: Write**
4. (있으면) **Inherit access from repository** 토글 ON — 패키지 ↔ repo 정식 link로 권한 자동 상속
5. GitHub Actions 실패한 run에서 **Re-run failed jobs** → 통과

### 예방 (다음 새 패키지 만들 때)
- `docker/metadata-action`이 자동으로 `org.opencontainers.image.source` 라벨 추가하긴 하지만, 그것만으론 자동 link 안 됨
- 새 GHCR 패키지 첫 push 직후 위 settings 한 번 들어가서 link 설정 권장

### 교훈
- "첫 push만 되고 그 다음부터 거부"는 **GHCR 패키지 권한 정책 누락의 전형 증상**
- GITHUB_TOKEN은 자동 무한 권한이 아니다 — 패키지가 만들어진 뒤엔 패키지 자체 정책이 우선
- 워크플로우 권한(`permissions: packages: write`)만 보고 안심 X — repository 차원 + 패키지 차원 둘 다 봐야 함

---

## TS-11. PowerShell에서 native exe(`gh`)에 multi-line body string 전달 — 인자 쪼개짐 + **인코딩 모지바케**

**날짜**: 2026-05-23 / 관련 작업: PR 본문 자동 작성. 2026-05-23 보강: stdin도 결국 인코딩 함정 있음.

### 증상 A — 인자 쪼개짐
- PowerShell에서 `gh pr create --body "..."` 실행 시 body 내 공백/괄호/한국어 토큰이 별도 인자로 쪼개짐:
  ```
  unknown arguments ["제품" "(선택) 추가..." "..." "..."]
  please quote all values that have spaces
  ```
- 큰따옴표로 감싸고 `@'...'@` here-string으로 만들었는데도 같은 에러

### 증상 B — 인코딩 깨짐 (가장 헷갈리는 함정)
- here-string + stdin (`$body | gh ... --body-file -`)으로 우회했더니 **명령은 통과하는데** GitHub PR 페이지 가서 보면 **한국어가 전부 `?`로 깨짐**:
  ```
  ?? PR ??: #4 (#1-3 ?? ?? ??, ?? ? PR ?? ...)
  ```
- 처음엔 GitHub UI 렌더링 문제로 의심하지만 — `gh pr view <N> --json body` 로 raw로 봐도 똑같이 `?`. 즉 GitHub에 저장된 본문 자체가 이미 깨진 상태로 들어감.

### 원인
**(A)** PowerShell이 native exe에 인자 전달 시 quoting/escaping이 cmd.exe 규칙과 충돌. 백틱·괄호·콜론·한국어 어절 경계가 섞이면 인자 경계를 잘못 추론.

**(B)** PowerShell 5.1의 `$OutputEncoding` 기본값은 **ASCII**(Korean Windows에선 CP949처럼 동작). `$str | native.exe` pipe 시 stdin은 이 인코딩으로 변환됨 → UTF-8 외 문자는 `?`로 손실. `--body-file -` 도 결국 stdin이라 같은 문제.

### 해결 — **임시 UTF-8 파일 + `--body-file <path>`**

가장 확실한 방법:
```powershell
# 1) Write 도구나 Set-Content -Encoding utf8 로 임시 파일 작성 (UTF-8)
$body = @'
**이번 PR 번호: #4**
세 가지 정리 작업 — 한국어/괄호/이모지 🤖 다 안전
'@
Set-Content -Path .pr-body.tmp.md -Value $body -Encoding utf8

# 2) gh pr edit/create 에 --body-file <path> 로 파일 경로 전달
& "C:\Program Files\GitHub CLI\gh.exe" pr create `
    --repo Owner/Repo `
    --title "title" `
    --body-file .pr-body.tmp.md

# 3) 끝나면 임시 파일 정리
Remove-Item .pr-body.tmp.md
```

`gh`가 파일을 직접 UTF-8로 읽으니 PowerShell의 인코딩 변환을 거치지 않음 → 한국어 안전.

### 차선책 — stdin 쓰려면 `$OutputEncoding` 강제

stdin 방식을 꼭 써야 하면 호출 직전에:
```powershell
$OutputEncoding = [System.Text.UTF8Encoding]::new()
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new()
$body | & gh pr create --body-file -
```
다만 세션 전역 영향이 있어 다른 스크립트와 충돌 가능 — **임시 파일 방식이 더 안전**.

### 깨진 PR 복구
이미 깨진 채로 올린 PR은 동일 방식(`--body-file <utf8-path>`)으로 `gh pr edit <N> --body-file ...` 하면 즉시 정상화.

### 추가 함정
- `& "C:\Program Files\GitHub CLI\gh.exe" ...` 실행 시 **cwd가 git repo가 아니면** `could not determine the current branch` 에러. `Set-Location` 또는 `--repo OWNER/REPO` 명시로 회피.

### 교훈
- PowerShell + native exe + multi-line/한국어 body = **임시 UTF-8 파일 + `--body-file <path>`** 가 정답
- stdin pipe(`--body-file -`)는 인자 쪼개짐은 해결하지만 **인코딩 손실은 해결 못 함**
- "명령이 성공"과 "결과물이 정상"은 다름 — 자동화 후 **반드시 `gh pr view`로 raw 본문 검증**
- 한국어 외에도 emoji, em-dash, smart quote 등 non-ASCII 전부 영향. ASCII-only면 두 증상 다 잠복

---

## TS-12. 동시 OPEN PR이 `plan.md`/`improvements.md` 같이 만지면 거의 100% 충돌

**날짜**: 2026-05-25 / 관련 작업: PR #6 (T3-18) + PR #7 (T2-11) 동시 진행

### 증상
- PR #6 (T3-18 카테고리 마스터) OPEN 중에 PR #7 (T2-11 N+1)을 main 기반으로 새 브랜치 시작
- PR #6 머지 후 PR #7을 `git rebase origin/main`
- `docs/plan.md` 3곳에서 conflict marker:
  - **진행 상태 표** (둘 다 새 줄 추가)
  - **새 섹션** (둘 다 `# T2-7 페이지네이션` 위에 새 섹션 삽입)
  - **이후 후보 큐** (한쪽이 항목 제거, 다른쪽이 다른 항목 추가)
- `docs/improvements.md`는 자동 머지 성공 (각자 다른 줄을 만져서)
- 코드 파일은 충돌 0건 (서로 다른 영역)

### 진단
- `plan.md`의 **진행 상태 표 / 새 섹션 / 이후 후보**는 거의 모든 PR이 만지는 영역 — 동시 진행 시 충돌 필연
- `improvements.md`의 Tier 표 항목별 체크박스는 PR마다 다른 줄을 만지면 자동 머지 가능
- 코드는 보통 서로 다른 도메인을 만져 충돌 드묾

### 해결

**rebase + 수동 충돌 해결 흐름**:
```bash
git checkout feat/your-branch
git fetch origin
git rebase origin/main
# CONFLICT in docs/plan.md → 파일 열어서 <<<<<<<, =======, >>>>>>> 마커 찾기

# 각 충돌 영역마다:
#  - 진행 상태 표: 두 PR의 새 줄을 모두 보존, 테스트 카운트는 합계로 갱신
#  - 새 섹션: 두 섹션 모두 보존, 구분선(---)으로 분리
#  - 이후 후보 큐: 머지된 PR이 추가한 항목은 보존, 내 PR로 완료된 항목은 제거

git add docs/plan.md docs/improvements.md
git rebase --continue
./gradlew test  # 테스트 카운트 합산이 맞는지 검증
git push --force-with-lease origin feat/your-branch
```

**검증**: `gh pr view <N> --json mergeable,mergeStateStatus` 로 `mergeable: MERGEABLE` 확인 (CI 진행 중엔 `mergeStateStatus: UNSTABLE`이지만 머지 자체는 가능).

### 예방 (CLAUDE.md "PR 만들기 전" 규칙 #4로 명문화)

새 PR이 `plan.md`/`improvements.md`를 만질 거면 우선순위:
1. **(권장)** OPEN PR 먼저 머지 후 main pull → 새 작업 시작
2. 시간 급하면 OPEN PR을 base branch로 새 PR 생성 (stacked PR — `gh pr create --base feat/xxx`)
3. 둘 다 안 되면 main 기반 + 위 rebase 흐름 각오

### 교훈
- **진행 추적 문서는 동시 PR의 천적**. 코드는 모듈별로 분리되지만 문서는 한 곳에 누적되니까
- `improvements.md` 같이 한 줄짜리 항목 체크박스는 비교적 안전, `plan.md`의 마크다운 표/섹션은 충돌 자석
- `--force-with-lease`는 `--force`보다 안전 — 다른 사람이 푸시한 게 있으면 거절. 혼자 작업하는 PR에서도 디폴트로 쓸 것

---

## TS-13. **머지된 PR의 브랜치에 추가 푸시 사고** — main에 안 들어가는 좀비 커밋

**날짜**: 2026-05-25 / 관련 작업: PR #7 머지 직후 `feat/n-plus-one-fix`에 추가 커밋 → PR #8로 수습

### 증상
- PR #7 (T2-11) 머지 직후, 같은 브랜치 `feat/n-plus-one-fix`에 후속 문서 커밋(`cf50a05`: CLAUDE.md 규칙 #4 + TS-12) 추가 푸시
- 머지된 PR은 재오픈되지 않음 → 푸시된 커밋은 **origin/feat/n-plus-one-fix에만 존재**, main에는 영원히 안 들어감
- 사용자가 "PR#7은 이미 머지했는데 거기다가 추가로 내용을 push하면 어떡하냐"로 발견할 때까지 인지 못 함
- `git status`는 "up to date with origin/feat/n-plus-one-fix"로 정상 보고 → 좀비 상태가 시각적으로 드러나지 않음

### 진단
- 머지된 PR은 GitHub에서 **CLOSED + MERGED** 상태로 잠김. 같은 브랜치에 추가 푸시해도 PR이 자동 reopen되지 않음
- 같은 브랜치명을 계속 쓰면 머지 후/전 구분이 안 됨 — 작업 흐름상 머지 직후 곧바로 다음 작업을 시작하면 "같은 브랜치 = 진행 중"이라는 무의식적 가정에 빠지기 쉬움
- 워크플로우 규칙 #1 (`gh pr list`로 상태 확인)을 **PR 생성 전에만** 적용하고 **push 전에는** 적용 안 한 게 근본 원인

### 해결
**좀비 커밋을 새 PR로 분리하기**:
```bash
# 1) main 최신화
git checkout main
git pull origin main

# 2) 새 브랜치 분기
git checkout -b docs/<topic>

# 3) 좀비 커밋만 cherry-pick (충돌 0인 게 정상 — 같은 main 위에 올라가는 거라)
git cherry-pick <sha>

# 4) 푸시 + 새 PR
git push -u origin docs/<topic>
# PR 본문은 UTF-8 임시 파일 + --body-file (TS-11)
gh pr create --base main --head docs/<topic> --title "..." --body-file .pr-body-N.tmp.md

# 5) 좀비 브랜치 삭제 (로컬 + 원격)
git branch -D feat/<old-branch>
git push origin --delete feat/<old-branch>
```

### 예방
- **`git push` 전 체크리스트** (특히 머지 직후 같은 브랜치에 작업 이어갈 때):
  1. `gh pr list --state all --limit 5` 로 현재 브랜치의 PR 상태 확인
  2. 해당 PR이 `MERGED`면 **그 브랜치는 이미 죽은 브랜치** — 새 작업은 `git checkout main && git pull && git checkout -b <new-branch>` 부터
  3. `git branch --show-current`로 어디서 작업 중인지 항상 확인
- **브랜치 이름 관성 금지**: "PR 머지됐는데 후속 한 줄 더 추가하자"는 욕구가 들 때, 자동으로 새 브랜치 만들기
- CLAUDE.md "PR 만들기 전" 규칙은 사실상 **"브랜치에 커밋 만들기 전"** 으로 적용해야 안전

### 교훈
- **머지된 브랜치는 좀비**. `git status`가 "정상"이라고 거짓말함 — 원격에는 있지만 main 흐름과 분리된 사이드 브랜치일 뿐
- 자동화 도구가 머지 상태를 항상 인지하지는 못함. 푸시 직전에도 `gh pr view <N> --json state` 같은 적극적 검증 필요
- 사고 자체는 cherry-pick 한 번이면 수습 가능. 문제는 **사용자가 발견하기 전까지 main에 누락된 채로 시간이 흐른다**는 것 — 자동화는 빠른 만큼 빠르게 틀린다
- `mergeStateStatus: UNSTABLE` 자체는 머지 차단 아님 — CI 진행 중일 때 잠시 나오는 상태. `mergeable: MERGEABLE`이면 안전
