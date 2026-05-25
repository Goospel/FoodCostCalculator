# EC2 배포 가이드 (Stage A — 비공개 베타)

목표: 친구 5~10명에게 보여줄 수 있는 수준으로 **무료 티어 EC2 1대에 띄우기**.
이 단계에서는 도메인/HTTPS 없이 IP로 접속. 도메인은 Stage B에서.

> 예상 비용: **12개월 무료** (t3.micro 750hr/월 × EC2 1대 한도 내).
> 12개월 후 약 $7-8/월.

---

## 1. AWS 콘솔에서 EC2 인스턴스 생성

### 1-1. 인스턴스 종류
- **Region**: 서울 (ap-northeast-2) 권장 — 국내 사용자 지연시간 ↓
- **AMI**: **Ubuntu Server 22.04 LTS (HVM), SSD Volume Type** — 프리티어 적격
- **Instance type**: **t3.micro** (vCPU 2, RAM 1GB) — 프리티어
  - t2.micro도 가능하지만 t3가 새 세대라 성능 약간 ↑
- **Key pair**: 새로 생성 후 `.pem` 파일 다운로드 → 안전한 곳에 보관 (잃으면 접속 불가)
  - Windows: `C:\Users\<나>\.ssh\coast-calculator.pem` 같은 위치 권장

### 1-2. 보안 그룹 (방화벽)
인바운드 규칙:
| 타입 | 프로토콜 | 포트 | 소스 | 용도 |
|---|---|---|---|---|
| SSH | TCP | 22 | **내 IP만** (현재 위치) | 관리자 접속 |
| HTTP | TCP | 80 | 0.0.0.0/0 | 웹 접속 |
| (선택) HTTPS | TCP | 443 | 0.0.0.0/0 | Stage B에서 |

⚠️ SSH는 절대 0.0.0.0/0 으로 열지 말 것 — 24시간 안에 공격 봇이 시도함.

### 1-3. 스토리지
- 기본 8GB → **20GB로 늘리기** 권장 (Docker 이미지 + MySQL 데이터)
- 프리티어 30GB까지 무료

### 1-4. (선택) Elastic IP
- 인스턴스 정지/재시작 시 public IP가 바뀌므로 고정 IP를 받고 싶다면 **Elastic IP** 할당
- 인스턴스에 **연결된 상태에선 무료**, 분리 후 보유만 하면 시간당 과금 (잊지 말고 해제)

---

## 2. SSH 접속

### Windows (Git Bash 또는 PowerShell)
```bash
# .pem 파일 권한 (Linux/Mac)
chmod 400 ~/.ssh/coast-calculator.pem

# 접속 (퍼블릭 IP는 콘솔에서 확인)
ssh -i ~/.ssh/coast-calculator.pem ubuntu@<EC2-PUBLIC-IP>
```

Windows PowerShell에서는 `.pem` 권한이 자동 처리되지 않을 수 있음 — 그 경우 [PuTTY](https://www.putty.org/)로 `.ppk` 변환 후 접속.

---

## 3. 서버 초기 셋업 (1회만)

EC2에 접속한 상태에서:

```bash
# 시스템 업데이트
sudo apt update && sudo apt upgrade -y

# Docker 설치 (공식 스크립트)
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker ubuntu

# Docker Compose 플러그인 확인 (위 스크립트에 포함됨)
docker compose version

# 한 번 로그아웃 후 재접속 (그룹 권한 적용)
exit
```

다시 SSH로 접속한 뒤:

```bash
# Git 설치
sudo apt install -y git

# 코드 가져오기
git clone https://github.com/<your-id>/coastCalculator.git
cd coastCalculator/coastCalculator/coastCalculator
```

> 만약 GitHub 비공개 저장소라면 SSH key 또는 Personal Access Token 필요.

---

## 4. 운영 환경변수 작성

```bash
# 템플릿 복사
cp .env.prod.example .env.prod

# 편집 — 안전한 비번 생성하려면 openssl rand 활용
openssl rand -base64 24   # 출력값을 비번에 사용
nano .env.prod
```

`.env.prod` 안에 채울 값:

```env
DB_ROOT_PASSWORD=<openssl로 생성한 강한 비번 #1>
DB_USERNAME=coast
DB_PASSWORD=<openssl로 생성한 강한 비번 #2>

INITIAL_ADMIN_USERNAME=admin
# 비워두면 부팅 시 랜덤 16자 생성 + 로그에 1회 노출됨 (권장)
INITIAL_ADMIN_PASSWORD=

# Naver Shopping API (있으면 채우고 MOCK_ENABLED=false, 없으면 비워두고 true 유지)
NAVER_CLIENT_ID=<발급받은 값>
NAVER_CLIENT_SECRET=<발급받은 값>
NAVER_MOCK_ENABLED=false

# 쿠팡 파트너스 가입 후 발급받은 ID (가입 전이면 비워둬도 됨)
COUPANG_TRACKING_ID=
```

⚠️ `.env.prod`는 `.gitignore`에 잡혀 있으니 절대 커밋되지 않음.

---

## 5. 빌드 & 실행

```bash
# 첫 빌드 (의존성 다운로드로 5-10분 걸림)
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build

# 로그 따라가기
docker compose -f docker-compose.prod.yml logs -f app
```

**부팅 첫 로그에서** `INITIAL_ADMIN_PASSWORD`를 비워뒀다면 다음과 같은 메시지가 출력됨:

```
WARN  =============================================================
WARN   INITIAL_ADMIN_PASSWORD 환경변수가 설정되지 않았습니다.
WARN   랜덤 비밀번호를 생성했습니다 — 지금 복사해서 보관하세요.
WARN   username: admin
WARN   password: <16자 랜덤>
WARN   이 로그는 부팅 시 1회만 출력됩니다.
WARN  =============================================================
```

→ **반드시 안전한 곳에 저장**. 다시 부팅해도 비번은 보존됨 (재출력 X).

---

## 6. 접속 테스트

브라우저에서:
```
http://<EC2-PUBLIC-IP>/
```

- 홈(레시피 허브) 표시되면 성공
- admin 로그인 → 위에서 받은 비번 사용
- `/admin/ingredients/fetch`에서 "밀가루" 키워드로 Naver fetch 시도

---

## 7. 운영 명령 모음

```bash
# 상태 확인
docker compose -f docker-compose.prod.yml ps

# 앱 로그 (최근 200줄)
docker compose -f docker-compose.prod.yml logs --tail=200 app

# 앱 재시작
docker compose -f docker-compose.prod.yml restart app

# 코드 업데이트 (git pull 후 재빌드)
git pull
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build

# DB 백업 (cron으로 매일 권장)
docker exec coast-mysql mysqldump -uroot -p$DB_ROOT_PASSWORD coast_calculator > backup_$(date +%Y%m%d).sql

# 전체 정지
docker compose -f docker-compose.prod.yml down

# 데이터까지 전부 삭제 (주의)
docker compose -f docker-compose.prod.yml down -v
```

---

## 8. 흔한 문제

### 8-1. `docker compose up` 중 OOM (Out of Memory)
t3.micro는 RAM 1GB. Gradle 빌드가 메모리 부족하면:
- 옵션 A: **로컬에서 빌드 후 이미지만 EC2로 push** (Docker Hub 무료 계정 활용)
- 옵션 B: **swap 추가**:
  ```bash
  sudo fallocate -l 2G /swapfile
  sudo chmod 600 /swapfile
  sudo mkswap /swapfile
  sudo swapon /swapfile
  echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
  ```

### 8-2. 80번 포트 권한 거부
Linux는 1024 이하 포트가 권한 필요. `docker-compose.prod.yml`에 `80:8080`으로 매핑하면 Docker가 알아서 처리하지만, 만약 실패하면 `8080:8080`으로 바꾸고 접속도 `http://IP:8080/`으로.

### 8-3. SSH 접속 안 됨
- 보안 그룹 인바운드 22 포트에 본인 IP가 있는지 (집 IP는 ISP에 따라 바뀌기도 함)
- AWS 콘솔에서 보안 그룹 → 인바운드 규칙 → "내 IP" 다시 선택

### 8-4. admin 비번 분실
컨테이너 안에 들어가 DB 비번을 재설정:
```bash
docker exec -it coast-mysql mysql -uroot -p$DB_ROOT_PASSWORD coast_calculator
> DELETE FROM users WHERE username='admin';
> EXIT
docker compose -f docker-compose.prod.yml restart app
# → 부팅 시 다시 admin 시드, 새 랜덤 비번이 로그에 출력됨
```

---

## 9. 시크릿 관리 (T1-4)

운영에서는 `SPRING_PROFILES_ACTIVE=prod` (docker-compose.prod.yml이 자동 주입)로 `ProductionSecretsValidator`가 활성화됩니다. 부팅 시 다음을 검증하고, 위반 시 즉시 부팅 중단:

1. DB 자격증명이 디폴트(`coast`/`coastpass`) 그대로면 거부 — `DB_USERNAME`/`DB_PASSWORD` 명시 필수
2. `INITIAL_ADMIN_PASSWORD`가 비어있으면 거부 — 운영에서 랜덤 비번 로그 노출은 회전 어려움 + 로그 유출 시 즉시 침해라 금지
3. `naver.api.mock-enabled=false`인데 `NAVER_CLIENT_ID`/`SECRET`가 비어있으면 거부

위반 시 부팅 로그에 모든 위반 사항이 한 번에 표시됩니다 (여러 환경변수가 빠졌을 때 한 번에 파악).

### 9-1. EC2에서 시크릿 파일 권한

`.env.prod`는 비번을 평문으로 담으므로 파일 자체 권한을 강하게:

```bash
# 소유자만 읽기/쓰기 (chmod 600)
chmod 600 .env.prod
# 소유자도 ubuntu만 (또는 root만)
sudo chown ubuntu:ubuntu .env.prod

# 백업 잡이 의도치 않게 카피하지 않게 — find로 점검
find /home/ubuntu -name ".env*" -ls
```

### 9-2. systemd 사용 시 (Docker 안 쓰는 경우 — 참고)

`docker compose`가 아닌 시스템 java 실행이라면 systemd `EnvironmentFile`로 주입:

```ini
# /etc/systemd/system/coastcalculator.service
[Service]
EnvironmentFile=/etc/coastcalculator/secrets.env
ExecStart=/usr/bin/java -jar /opt/coastcalculator/app.jar
User=app
Group=app
```

```bash
# 권한 — root:app, group read-only (app 사용자만 읽음)
sudo chown root:app /etc/coastcalculator/secrets.env
sudo chmod 640 /etc/coastcalculator/secrets.env
```

### 9-3. GitHub Actions Secrets

CI/CD에서 쓰는 시크릿은 GitHub repo Settings → Secrets and variables → Actions에 등록:

| 시크릿 | 용도 | 설정 위치 |
|---|---|---|
| `GITHUB_TOKEN` | GHCR 이미지 push (자동 생성됨) | 기본 |
| (선택) `EC2_SSH_KEY` | 배포 자동화 시 EC2 ssh | 수동 등록 |
| (선택) `EC2_HOST` | EC2 IP/도메인 | 수동 등록 |

운영 시크릿(`DB_PASSWORD`, `NAVER_*` 등)은 **CI에 노출하지 말 것** — EC2 위에서만 다룸. CI는 빌드+이미지 push만, 시크릿은 운영 서버에 직접.

### 9-4. 시크릿 회전 절차

| 시크릿 | 회전 주기 | 절차 |
|---|---|---|
| `INITIAL_ADMIN_PASSWORD` | 분기 1회 또는 의심 사고 시 | `.env.prod` 갱신 → 컨테이너 재시작 → admin 로그인 후 비번 변경 (DB에 BCrypt 해시 갱신) |
| `DB_PASSWORD` | 6개월 1회 | (1) MySQL에서 사용자 비번 변경 → (2) `.env.prod` 갱신 → (3) 앱 재시작 |
| `NAVER_CLIENT_SECRET` | Naver 콘솔에서 재발급 시 | Naver 콘솔에서 재발급 → `.env.prod` 갱신 → 앱 재시작 |
| `COUPANG_TRACKING_ID` | 일반적으로 영구 | 변경 거의 없음 |

회전 후 **이전 컨테이너 로그에 비번이 남아있지 않은지** 확인 (`docker compose logs app | grep -i password`).

### 9-5. 외부 저장소 통합 hook (미도입 — 운영 단계 진입 시)

현재는 환경변수 + `.env.prod` 파일로 충분하지만, 운영 규모 커지면 외부 저장소 도입 옵션:

**AWS Secrets Manager** (EC2/ECS 운영 시 자연 fit):
- Spring Boot 4 기본 지원: `spring.config.import=optional:aws-secretsmanager:coastcalculator/prod`
- 의존성: `org.springframework.cloud:spring-cloud-aws-starter-secrets-manager`
- EC2 IAM Role에 `secretsmanager:GetSecretValue` 권한 부여 → 환경변수 없이 코드에서 자동 주입
- 비용: 시크릿당 $0.40/월 + 호출당 $0.05/만건 (사실상 무시)

**HashiCorp Vault** (셀프 호스팅):
- 의존성: `org.springframework.cloud:spring-cloud-starter-vault-config`
- `spring.config.import=optional:vault://...`
- 운영 복잡도 ↑ (Vault 서버 자체 관리)

**SOPS / git-crypt** (시크릿 파일 암호화 후 git에 저장):
- `.env.prod.enc`를 커밋, 복호화 키만 별도 관리
- CI/CD에서 복호화 후 컨테이너에 주입

→ 도입 시점은 **운영 인스턴스 수 ≥ 2** 또는 **시크릿 종류 ≥ 10개** 즈음 검토. 현재는 의도적으로 단순 유지.

---

## 10. 다음 단계 (Stage B 진입 시 추가 작업)

- **도메인 + Route53**: 도메인 구매 → A record로 EC2 IP 가리키기
- **HTTPS**: Caddy 또는 Nginx + Let's Encrypt 자동 발급
- **T1-1 Flyway**: 스키마 마이그레이션 도구 도입 (✅ 완료)
- **T1-3 비번 정책 / brute force 방어** (✅ 완료)
- **T1-6 통합 테스트 3종** (배포 안전망) (✅ 완료)
- **이미지 S3 이전** (현재 로컬 디스크 → EC2 사라지면 같이 사라짐)
- **T1-4 외부 저장소** (AWS SM 또는 Vault) — § 9-5 참조

→ Stage B 진입 조건: 친구 베타 1주 운영 → 사용자 피드백으로 진행 결정.
