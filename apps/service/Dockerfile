# ── 构建阶段 ────────────────────────────────────────────
FROM bellsoft/liberica-openjdk-alpine:25 AS builder
WORKDIR /build

# 先复制 pom 利用 layer 缓存
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY aaf-dependencies/pom.xml aaf-dependencies/
COPY aaf-common/pom.xml aaf-common/
COPY aaf-framework/pom.xml aaf-framework/
COPY aaf-auto-dev/pom.xml aaf-auto-dev/
COPY aaf-api/pom.xml aaf-api/

RUN ./mvnw dependency:go-offline -q

COPY . .
RUN ./mvnw package -DskipTests -q

# ── 运行阶段 ────────────────────────────────────────────
FROM bellsoft/liberica-runtime-container:jdk-all-25-slim-musl AS runner
WORKDIR /app

RUN addgroup -S aaf && adduser -S aaf -G aaf
USER aaf

COPY --from=builder /build/aaf-api/target/aaf-api-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:InitialRAMPercentage=25.0", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+UseG1GC", \
  "-XX:MaxGCPauseMillis=200", \
  "-XX:+HeapDumpOnOutOfMemoryError", \
  "-XX:HeapDumpPath=/tmp/heap-dumps", \
  "-XX:+ExitOnOutOfMemoryError", \
  "-Dfile.encoding=UTF-8", \
  "-Duser.timezone=Asia/Shanghai", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
