# ── 构建阶段 ────────────────────────────────────────────
FROM bellsoft/liberica-openjdk-alpine:25 AS builder
WORKDIR /build

COPY . .
RUN chmod +x mvnw
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw package -DskipTests -q

# ── 运行阶段 ────────────────────────────────────────────
FROM bellsoft/liberica-runtime-container:jdk-all-25-slim-musl AS runner
WORKDIR /app

COPY --from=builder /build/aaf-api/target/aaf-api-*.jar app.jar

RUN mkdir -p /data/files

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
