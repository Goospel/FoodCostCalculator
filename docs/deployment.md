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

## 9. 다음 단계 (Stage B 진입 시 추가 작업)

- **도메인 + Route53**: 도메인 구매 → A record로 EC2 IP 가리키기
- **HTTPS**: Caddy 또는 Nginx + Let's Encrypt 자동 발급
- **T1-1 Flyway**: 스키마 마이그레이션 도구 도입
- **T1-3 비번 정책 / brute force 방어**
- **T1-6 통합 테스트 3종** (배포 안전망)
- **이미지 S3 이전** (현재 로컬 디스크 → EC2 사라지면 같이 사라짐)

→ Stage B 진입 조건: 친구 베타 1주 운영 → 사용자 피드백으로 진행 결정.
