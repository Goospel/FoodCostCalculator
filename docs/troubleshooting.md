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
- `improvements.md` T2-11 (N+1 점검)에 잔존 위험 추적 중

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
