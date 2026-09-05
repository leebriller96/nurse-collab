# ── 빌드 단계 ────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk AS build
WORKDIR /build

# 의존성만 먼저 받아 레이어로 굳힌다. 소스만 고쳤을 때 재다운로드하지 않기 위해서다.
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies

COPY src src
# 테스트는 CI 에서 돈다. 이미지 빌드 안에는 Testcontainers 가 쓸 도커가 없다.
RUN ./gradlew --no-daemon bootJar -x test

# ── 실행 단계 ────────────────────────────────────────────────
FROM eclipse-temurin:21-jre
WORKDIR /app

# 로그와 기록 시각이 병원 현지 시각과 맞아야 한다
ENV TZ=Asia/Seoul

# compose 헬스체크가 컨테이너 안에서 실행된다. JRE 이미지에는 curl 이 없다.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# 애플리케이션이 root 로 돌 이유가 없다
RUN useradd --system --create-home --shell /usr/sbin/nologin app
USER app

COPY --from=build /build/build/libs/*.jar app.jar

EXPOSE 8080

# 힙 상한은 compose 의 JAVA_TOOL_OPTIONS 로 정한다.
# 여기에 박아 두면 ENTRYPOINT 인자가 뒤에 와서 환경변수를 덮어쓰기 때문에
# 작은 서버에 맞춰 줄일 수가 없다. 한 대에 DB·Redis 가 같이 도는 구성이라
# JVM 이 램의 75% 를 쥐면 서버가 통째로 OOM 으로 죽는다.
ENTRYPOINT ["java", "-jar", "app.jar"]
