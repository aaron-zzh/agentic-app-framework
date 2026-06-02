package com.xuejiai.aaf.autodev.git;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/** CI/CD 集成服务——触发 Pipeline、查询状态、处理 Webhook、触发部署。 */
@Slf4j
@Service
public class CiCdService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @Value("${aaf.autodev.github.token:}")
    private String githubToken;

    @Value("${aaf.autodev.github.repo:}")
    private String githubRepo;

    /** 构建状态缓存（runId → status） */
    private final Map<Long, BuildStatus> buildCache = new ConcurrentHashMap<>();

    /** 触发 GitHub Actions workflow */
    public Long triggerWorkflow(String workflowFile, String ref, Map<String, String> inputs) {
        var inputsJson = inputs != null ? MAPPER.valueToTree(inputs).toString() : "{}";
        var payload =
                """
                {"ref":"%s","inputs":%s}"""
                        .formatted(ref, inputsJson);

        var request =
                githubRequest(
                        "POST",
                        "/actions/workflows/%s/dispatches".formatted(workflowFile),
                        payload);
        try {
            var response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 204) {
                log.info("CI 触发成功: workflow={} ref={}", workflowFile, ref);
                // GitHub 不返回 context ID，需要查询最新 context
                return queryLatestRunId(workflowFile, ref);
            }
            log.warn("CI 触发失败: HTTP {} - {}", response.statusCode(), response.body());
            return null;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("CI 触发异常: {}", e.getMessage());
            return null;
        }
    }

    /** 查询构建状态 */
    public BuildStatus getStatus(Long runId) {
        var cached = buildCache.get(runId);
        if (cached != null && cached.isTerminal()) return cached;

        var request = githubRequest("GET", "/actions/runs/%d".formatted(runId), null);
        try {
            var response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                var json = MAPPER.readTree(response.body());
                var status = new BuildStatus();
                status.setRunId(runId);
                status.setStatus(json.get("status").asText());
                status.setConclusion(
                        json.has("conclusion") && !json.get("conclusion").isNull()
                                ? json.get("conclusion").asText()
                                : null);
                status.setHtmlUrl(json.get("html_url").asText());
                status.setUpdatedAt(LocalDateTime.now());
                buildCache.put(runId, status);
                return status;
            }
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return cached;
    }

    /** 处理 GitHub Webhook 回调（workflow_run 事件） */
    public void handleWebhook(String event, JsonNode payload) {
        if (!"workflow_run".equals(event)) return;

        var action = payload.get("action").asText();
        var run = payload.get("workflow_run");
        var runId = run.get("id").asLong();

        var status = new BuildStatus();
        status.setRunId(runId);
        status.setStatus(run.get("status").asText());
        status.setConclusion(
                run.has("conclusion") && !run.get("conclusion").isNull()
                        ? run.get("conclusion").asText()
                        : null);
        status.setHtmlUrl(run.get("html_url").asText());
        status.setUpdatedAt(LocalDateTime.now());
        buildCache.put(runId, status);

        log.info(
                "Webhook: workflow_run {} runId={} status={} conclusion={}",
                action,
                runId,
                status.getStatus(),
                status.getConclusion());
    }

    /** 触发部署（调用 deploy workflow） */
    public Long triggerDeploy(String environment, String ref) {
        return triggerWorkflow("deploy.yml", ref, Map.of("environment", environment));
    }

    /** 获取最近 N 次构建 */
    public List<BuildStatus> recentBuilds(int limit) {
        return buildCache.values().stream()
                .sorted((a, b) -> b.getUpdatedAt().compareTo(a.getUpdatedAt()))
                .limit(limit)
                .toList();
    }

    private Long queryLatestRunId(String workflowFile, String ref) {
        var request =
                githubRequest(
                        "GET",
                        "/actions/workflows/%s/runs?branch=%s&per_page=1"
                                .formatted(workflowFile, ref),
                        null);
        try {
            Thread.sleep(2000); // 等待 GitHub 创建 context
            var response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                var runs = MAPPER.readTree(response.body()).get("workflow_runs");
                if (runs.isArray() && !runs.isEmpty()) {
                    return runs.get(0).get("id").asLong();
                }
            }
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return null;
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
