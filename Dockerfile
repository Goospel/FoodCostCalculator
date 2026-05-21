# =========================================================
# Stage 1: 빌드 — JDK 25로 bootJar 생성
# =========================================================
FROM eclipse-temurin:25-jdk AS builder

WORKDIR /workspace

# Gradle wrapper와 의존성 정의 파일만 먼저 복사하여 캐시 활용
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle

# 권한 부여 (Windows에서 만든 wrapper일 경우 대비)
RUN chmod +x ./gradlew

# 의존성을 먼저 받아 캐시 레이어로 굳힘
RUN ./gradlew dependencies --no-daemon || true

# 소스 복사 후 빌드
COPY src src
RUN ./gradlew bootJar --no-daemon -x test

# =========================================================
# Stage 2: 런타임 — JRE 25 slim에 jar만 복사
# =========================================================
FROM eclipse-temurin:25-jre AS runtime

# 비루트 사용자로 실행 (보안 권장)
RUN groupadd --system app && useradd --system --gid app --create-home app

WORKDIR /app

# 빌더에서 jar만 복사
COPY --from=builder /workspace/build/libs/*-SNAPSHOT.jar app.jar

# uploads 디렉토리 미리 생성 + 권한
RUN mkdir -p /app/uploads && chown -R app:app /app

USER app

# 컨테이너 환경 기본값 (docker-compose에서 override 가능)
ENV SERVER_PORT=8080
ENV UPLOAD_DIR=/app/uploads
ENV JAVA_OPTS="-Xms256m -Xmx512m"

EXPOSE 8080

# Heap 옵션은 JAVA_OPTS로 컨테이너 메모리 한도에 맞게 조정 가능
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
