package com.xuejiai.aaf.autodev.git;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import com.github.benmanes.caffeine.cache.Caffeine;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.util.JsonUtils;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

/** CI/CD 集成服务——触发 Pipeline、查询状态、处理 Webhook、触发部署。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CiCdService {

    private static final long MAX_CACHED_BUILDS = 1_000;
    private static final Duration BUILD_CACHE_TTL = Duration.ofHours(24);
    private static final Duration RUN_ID_POLL_DELAY = Duration.ofSeconds(2);
    private static final String GITHUB_SIGNATURE_PREFIX = "sha256=";

    /**
     * M7：改为实例字段而非 static，与项目其余服务的注入风格一致，也便于测试替换。
     * connectTimeout 避免 GitHub API 不可达时无限等待占用调用线程。
     */
    private final HttpClient http =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private final JsonMapper jsonMapper;
    private final TaskScheduler taskScheduler;

    @Value("${aaf.autodev.github.token:}")
    private String githubToken;

    @Value("${aaf.autodev.github.repo:}")
    private String githubRepo;

    @Value("${aaf.autodev.github.webhook-secret:}")
    private String githubWebhookSecret;

    @Value("${aaf.autodev.deploy.allowed-environments:}")
    private String allowedDeploymentEnvironments;

    @Value("${aaf.autodev.deploy.production-environments:production,prod}")
    private String productionDeploymentEnvironments;

    /** 构建状态缓存（runId → status），限制容量并在写入 24 小时后淘汰。 */
    private final Map<Long, BuildStatus> buildCache =
            Caffeine.newBuilder()
                    .maximumSize(MAX_CACHED_BUILDS)
                    .expireAfterWrite(BUILD_CACHE_TTL)
                    .<Long, BuildStatus>build()
                    .asMap();

    /** 触发 GitHub Actions workflow。异步等待 GitHub 创建 run 记录，不阻塞调用线程。 */
    public java.util.concurrent.CompletableFuture<Long> triggerWorkflow(
            String workflowFile, String ref, Map<String, String> inputs) {
        var payload = buildWorkflowPayload(ref, inputs);

        var request =
                githubRequest(
                        "POST",
                        "/actions/workflows/%s/dispatches".formatted(workflowFile),
                        payload);
        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenCompose(
                        response -> {
                            if (response.statusCode() == 204) {
                                log.info("CI 触发成功: workflow={} ref={}", workflowFile, ref);
                                // GitHub 不返回 context ID，需要延迟查询最新 context
                                return queryLatestRunId(workflowFile, ref);
                            }
                            log.warn(
                                    "CI 触发失败: HTTP {} - {}",
                                    response.statusCode(),
                                    response.body());
                            return java.util.concurrent.CompletableFuture.completedFuture(null);
                        })
                .exceptionally(
                        e -> {
                            log.error("CI 触发异常: {}", e.getMessage());
                            return null;
                        });
    }

    String buildWorkflowPayload(String ref, Map<String, String> inputs) {
        var payload = jsonMapper.createObjectNode();
        payload.put("ref", ref);
        var inputNode = jsonMapper.valueToTree(inputs != null ? inputs : Map.of());
        payload.set("inputs", inputNode);
        return payload.toString();
    }

    /** 查询构建状态 */
    public BuildStatus getStatus(Long runId) {
        var cached = buildCache.get(runId);
        if (cached != null && cached.isTerminal()) return cached;

        var request = githubRequest("GET", "/actions/runs/%d".formatted(runId), null);
        try {
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                var json = JsonUtils.readTree(response.body());
                var status = new BuildStatus();
                status.setRunId(runId);
                status.setStatus(json.get("status").asString());
                status.setConclusion(
                        json.has("conclusion") && !json.get("conclusion").isNull()
                                ? json.get("conclusion").asString()
                                : null);
                status.setHtmlUrl(json.get("html_url").asString());
                status.setUpdatedAt(LocalDateTime.now());
                buildCache.put(runId, status);
                return status;
            }
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return cached;
    }

    /** 处理已通过 HMAC-SHA256 验签的 GitHub Webhook 回调。 */
    public void handleWebhook(String event, String signature, byte[] payloadBytes) {
        verifyGithubWebhook(signature, payloadBytes);
        if (!"workflow_run".equals(event)) return;

        var payload = JsonUtils.readTree(new String(payloadBytes, StandardCharsets.UTF_8));
        var action = payload.get("action").asString();
        var run = payload.get("workflow_run");
        var runId = run.get("id").asLong();

        var status = new BuildStatus();
        status.setRunId(runId);
        status.setStatus(run.get("status").asString());
        status.setConclusion(
                run.has("conclusion") && !run.get("conclusion").isNull()
                        ? run.get("conclusion").asString()
                        : null);
        status.setHtmlUrl(run.get("html_url").asString());
        status.setUpdatedAt(LocalDateTime.now());
        buildCache.put(runId, status);

        log.info(
                "Webhook: workflow_run {} runId={} status={} conclusion={}",
                action,
                runId,
                status.getStatus(),
                status.getConclusion());
    }

    /** 触发部署（调用 deploy workflow）。环境必须在服务端白名单内。 */
    public java.util.concurrent.CompletableFuture<Long> triggerDeploy(
            String environment, String ref) {
        var normalizedEnvironment = normalizeEnvironment(environment);
        if (!configuredEnvironments(allowedDeploymentEnvironments)
                .contains(normalizedEnvironment)) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "不允许的部署环境");
        }
        return triggerWorkflow(
                "deploy.yml", ref, Map.of("environment", normalizedEnvironment));
    }

    /** 普通管理员只能部署白名单中的非生产环境；生产环境仅 SUPER_ADMIN 可部署。 */
    public boolean canAdminDeploy(String environment) {
        if (environment == null || environment.isBlank()) {
            return false;
        }
        var normalizedEnvironment = environment.trim().toLowerCase();
        return configuredEnvironments(allowedDeploymentEnvironments)
                        .contains(normalizedEnvironment)
                && !configuredEnvironments(productionDeploymentEnvironments)
                        .contains(normalizedEnvironment);
    }

    private void verifyGithubWebhook(String signature, byte[] payloadBytes) {
        if (githubWebhookSecret == null
                || githubWebhookSecret.isBlank()
                || signature == null
                || !signature.startsWith(GITHUB_SIGNATURE_PREFIX)) {
            throw new BusinessException(GlobalErrorCode.UNAUTHORIZED);
        }

        final byte[] actualSignature;
        try {
            actualSignature =
                    HexFormat.of().parseHex(signature.substring(GITHUB_SIGNATURE_PREFIX.length()));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(GlobalErrorCode.UNAUTHORIZED);
        }

        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(
                    new SecretKeySpec(
                            githubWebhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            var expectedSignature = mac.doFinal(payloadBytes);
            if (!MessageDigest.isEqual(expectedSignature, actualSignature)) {
                throw new BusinessException(GlobalErrorCode.UNAUTHORIZED);
            }
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("无法校验 GitHub Webhook 签名", e);
        }
    }

    private String normalizeEnvironment(String environment) {
        if (environment == null || environment.isBlank()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "部署环境不能为空");
        }
        return environment.trim().toLowerCase();
    }

    private Set<String> configuredEnvironments(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(environment -> !environment.isEmpty())
                .map(String::toLowerCase)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /** 获取最近 N 次构建 */
    public List<BuildStatus> recentBuilds(int limit) {
        return buildCache.values().stream()
                .sorted((a, b) -> b.getUpdatedAt().compareTo(a.getUpdatedAt()))
                .limit(limit)
                .toList();
    }

    private java.util.concurrent.CompletableFuture<Long> queryLatestRunId(
            String workflowFile, String ref) {
        var request =
                githubRequest(
                        "GET",
                        "/actions/workflows/%s/runs?branch=%s&per_page=1"
                                .formatted(workflowFile, ref),
                        null);
        // M7：原实现用 Thread.sleep(2000) 阻塞调用线程等待 GitHub 创建 run 记录——若从 HTTP 请求线程
        // 触发（GitController#triggerCi 同步调用），会占用 Tomcat 工作线程 2 秒，影响吞吐。
        // 改为 TaskScheduler 延迟调度 + HttpClient 异步发送，调用线程不阻塞。
        var future = new java.util.concurrent.CompletableFuture<Long>();
        taskScheduler.schedule(
                () ->
                        http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                                .thenAccept(
                                        response -> {
                                            if (response.statusCode() == 200) {
                                                var runs =
                                                        JsonUtils.readTree(response.body())
                                                                .get("workflow_runs");
                                                if (runs.isArray() && !runs.isEmpty()) {
                                                    future.complete(
                                                            runs.get(0).get("id").asLong());
                                                    return;
                                                }
                                            }
                                            future.complete(null);
                                        })
                        .exceptionally(
                                e -> {
                                    future.complete(null);
                                    return null;
                                }),
                java.time.Instant.now().plus(RUN_ID_POLL_DELAY));
        return future;
    }

    private HttpRequest githubRequest(String method, String path, String body) {
        var builder =
                HttpRequest.newBuilder()
                        .uri(
                                URI.create(
                                        "https://api.github.com/repos/%s%s"
                                                .formatted(githubRepo, path)))
                        .header("Authorization", "Bearer " + githubToken)
                        .header("Accept", "application/vnd.github+json");
        if ("POST".equals(method) && body != null) {
            builder.POST(HttpRequest.BodyPublishers.ofString(body));
        } else {
            builder.GET();
        }
        return builder.build();
    }

    /** 构建状态 */
    @Getter
    @Setter
    public static class BuildStatus {
        private Long runId;
        private String status; // queued/in_progress/completed
        private String conclusion; // success/failure/cancelled
        private String htmlUrl;
        private LocalDateTime updatedAt;

        public boolean isTerminal() {
            return "completed".equals(status);
        }
    }
}
