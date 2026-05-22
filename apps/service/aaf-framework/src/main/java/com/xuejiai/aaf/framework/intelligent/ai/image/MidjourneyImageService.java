package com.xuejiai.aaf.framework.intelligent.ai.image;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Midjourney 图像生成服务（通过 mj-proxy 兼容代理，如 holdai.top）。
 *
 * <p>流程：提交 imagine 任务 → 返回 taskId → 轮询 queryTask 获取结果
 *
 * <p>启用条件：配置 {@code aaf.ai.midjourney.enabled=true}
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "aaf.ai.midjourney.enabled", havingValue = "true")
public class MidjourneyImageService {

    private final MidjourneyProperties props;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MidjourneyImageService(MidjourneyProperties props) {
        this.props = props;
    }

    // ========== 响应 DTO ==========

    public record SubmitResult(String code, String description, String result) {
        public boolean isSuccess() {
            return "1".equals(code) || "21".equals(code) || "22".equals(code);
        }
    }

    public record TaskStatus(
            String id,
            String status,   // NOT_START / SUBMITTED / IN_PROGRESS / FAILURE / SUCCESS
            String imageUrl,
            String progress,
            String failReason,
            List<Map<String, Object>> buttons) {

        public boolean isDone() {
            return "SUCCESS".equals(status) || "FAILURE".equals(status);
        }
    }

    // ========== 核心方法 ==========

    /** 提交文生图任务，返回 taskId */
    public String imagine(String prompt) {
        return imagine(prompt, null);
    }

    /** 提交文生图任务（支持垫图），返回 taskId */
    public String imagine(String prompt, List<String> base64Images) {
        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("prompt", prompt);
        if (base64Images != null && !base64Images.isEmpty()) body.put("base64Array", base64Images);
        if (props.getNotifyUrl() != null) body.put("notifyHook", props.getNotifyUrl());

        var result = post("/submit/imagine", body, SubmitResult.class);
        if (!result.isSuccess()) throw new RuntimeException("Midjourney 任务提交失败: " + result.description());
        log.info("Midjourney imagine 提交成功: taskId={}", result.result());
        return result.result();
    }

    /** 执行后续操作（放大/变体/重绘），返回新 taskId */
    public String action(String taskId, String customId) {
        var result = post("/submit/action", Map.of("taskId", taskId, "customId", customId), SubmitResult.class);
        if (!result.isSuccess()) throw new RuntimeException("Midjourney action 失败: " + result.description());
        return result.result();
    }

    /** 查询单个任务状态 */
    public TaskStatus queryTask(String taskId) {
        return get("/task/" + taskId + "/fetch", TaskStatus.class);
    }

    /** 批量查询任务状态 */
    public List<TaskStatus> queryTasks(List<String> taskIds) {
        return post("/task/list-by-condition", Map.of("ids", taskIds),
                new TypeReference<List<TaskStatus>>() {});
    }

    // ========== 内部方法 ==========

    private <T> T post(String path, Object body, Class<T> type) {
        try {
            var json = objectMapper.writeValueAsString(body);
            var request = buildRequest(path).POST(HttpRequest.BodyPublishers.ofString(json)).build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            checkStatus(response);
            return objectMapper.readValue(response.body(), type);
        } catch (Exception e) {
            throw new RuntimeException("Midjourney POST " + path + " 失败: " + e.getMessage(), e);
        }
    }

    private <T> T post(String path, Object body, TypeReference<T> type) {
        try {
            var json = objectMapper.writeValueAsString(body);
            var request = buildRequest(path).POST(HttpRequest.BodyPublishers.ofString(json)).build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            checkStatus(response);
            return objectMapper.readValue(response.body(), type);
        } catch (Exception e) {
            throw new RuntimeException("Midjourney POST " + path + " 失败: " + e.getMessage(), e);
        }
    }

    private <T> T get(String path, Class<T> type) {
        try {
            var request = buildRequest(path).GET().build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            checkStatus(response);
            return objectMapper.readValue(response.body(), type);
        } catch (Exception e) {
            throw new RuntimeException("Midjourney GET " + path + " 失败: " + e.getMessage(), e);
        }
    }

    private HttpRequest.Builder buildRequest(String path) {
        return HttpRequest.newBuilder()
                .uri(URI.create(props.getBaseUrl() + path))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + props.getApiKey());
    }

    private void checkStatus(HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("Midjourney API HTTP " + response.statusCode() + ": " + response.body());
        }
    }
}
