package com.xuejiai.aaf.framework.intelligent.ai.image;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.intelligent.core.model.ModelManagementService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;

/**
 * Midjourney 异步图像生成服务（通过 mj-proxy 兼容代理）。
 *
 * <p>实现 {@link AsyncImageGenerationService}，接入统一异步图像生成路径。
 *
 * <p>模型配置来自 ai_model 表（providerType=MIDJOURNEY），baseUrl/apiKey 在模型记录中维护。
 *
 * <p>流程：submitTask → 返回 taskId → 轮询 queryTask
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MidjourneyAsyncImageService implements AsyncImageGenerationService {

    private final ModelManagementService modelManagementService;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Override
    public String submitTask(AsyncImageRequest request) {
        var model = modelManagementService.getModel(request.modelId());
        String baseUrl = model.effectiveBaseUrl();
        String apiKey = model.effectiveApiKey();

        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("prompt", request.prompt());
        try {
            var json = JsonUtils.toJsonString(body);
            var resp = post(baseUrl, apiKey, "/submit/imagine", json);
            String taskId = JsonUtils.readTree(resp).path("result").asString(null);
            if (taskId == null || taskId.isBlank()) {
                throw new RuntimeException("Midjourney 任务提交失败: " + resp);
            }
            log.info("[Midjourney] 任务提交成功: modelId={}, taskId={}", request.modelId(), taskId);
            return taskId;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Midjourney 提交失败: " + e.getMessage(), e);
        }
    }

    @Override
    public AsyncImageResult queryTask(String taskId) {
        // taskId 格式：{modelId}:{mjTaskId}，分隔符用于找到对应模型配置
        int sep = taskId.indexOf(':');
        if (sep < 0) return AsyncImageResult.failed(taskId, "无效 taskId 格式");
        String modelId = taskId.substring(0, sep);
        String mjTaskId = taskId.substring(sep + 1);

        var model = modelManagementService.getModel(modelId);
        String baseUrl = model.effectiveBaseUrl();
        String apiKey = model.effectiveApiKey();

        try {
            var resp = get(baseUrl, apiKey, "/task/" + mjTaskId + "/fetch");
            var node = JsonUtils.readTree(resp);
            String status = node.path("status").asString("UNKNOWN");
            return switch (status) {
                case "SUCCESS" -> {
                    String imageUrl = node.path("imageUrl").asString(null);
                    log.info("[Midjourney] 任务完成: taskId={}, url={}", mjTaskId, imageUrl);
                    yield AsyncImageResult.succeeded(taskId, imageUrl);
                }
                case "FAILURE" -> {
                    String failReason = node.path("failReason").asString("未知原因");
                    log.warn("[Midjourney] 任务失败: taskId={}, reason={}", mjTaskId, failReason);
                    yield AsyncImageResult.failed(taskId, failReason);
                }
                default -> AsyncImageResult.pending(taskId);
            };
        } catch (Exception e) {
            log.error("[Midjourney] 查询失败: taskId={}", mjTaskId, e);
            return AsyncImageResult.failed(taskId, e.getMessage());
        }
    }

    // ===== Midjourney 专有方法（AiImageService 使用）=====

    /** 提交文生图任务，返回 taskId。modelId 从 AiModel 表中查找 MIDJOURNEY 类型的模型。 */
    public String imagine(String modelId, String prompt, List<String> base64Images) {
        var model = modelManagementService.getModel(modelId);
        String baseUrl = model.effectiveBaseUrl();
        String apiKey = model.effectiveApiKey();

        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("prompt", prompt);
        if (base64Images != null && !base64Images.isEmpty()) body.put("base64Array", base64Images);
        try {
            var json = JsonUtils.toJsonString(body);
            var resp = post(baseUrl, apiKey, "/submit/imagine", json);
            var node = JsonUtils.readTree(resp);
            if (!List.of("1", "21", "22").contains(node.path("code").asString())) {
                throw new RuntimeException(
                        "Midjourney 提交失败: " + node.path("description").asString());
            }
            String taskId = node.path("result").asString(null);
            log.info("[Midjourney] imagine 提交成功: modelId={}, taskId={}", modelId, taskId);
            return taskId;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Midjourney imagine 失败: " + e.getMessage(), e);
        }
    }

    /** 执行后续操作（放大/变体），返回新 taskId。 */
    public String action(String modelId, String taskId, String customId) {
        var model = modelManagementService.getModel(modelId);
        try {
            var body = Map.of("taskId", taskId, "customId", customId);
            var json = JsonUtils.toJsonString(body);
            var resp =
                    post(model.effectiveBaseUrl(), model.effectiveApiKey(), "/submit/action", json);
            var node = JsonUtils.readTree(resp);
            if (!List.of("1", "21", "22").contains(node.path("code").asString())) {
                throw new RuntimeException(
                        "Midjourney action 失败: " + node.path("description").asString());
            }
            return node.path("result").asString(null);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Midjourney action 失败: " + e.getMessage(), e);
        }
    }

    /** 批量查询任务状态。 */
    public List<MjTaskStatus> queryTasks(String modelId, List<String> taskIds) {
        var model = modelManagementService.getModel(modelId);
        try {
            var body = Map.of("ids", taskIds);
            var json = JsonUtils.toJsonString(body);
            var resp =
                    post(
                            model.effectiveBaseUrl(),
                            model.effectiveApiKey(),
                            "/task/list-by-condition",
                            json);
            return JsonUtils.parseObject(resp, new TypeReference<List<MjTaskStatus>>() {});
        } catch (Exception e) {
            throw new RuntimeException("Midjourney 批量查询失败: " + e.getMessage(), e);
        }
    }

    /** Midjourney 任务状态。 */
    public record MjTaskStatus(
            String id,
            String status,
            String imageUrl,
            String progress,
            String failReason,
            List<Map<String, Object>> buttons) {
        public boolean isDone() {
            return "SUCCESS".equals(status) || "FAILURE".equals(status);
        }
    }

    // ===== 工具方法 =====

    /** 校验回调密钥（fail-closed）。 */
    public boolean verifyNotify(String configuredSecret, String providedSecret) {
        if (configuredSecret == null || configuredSecret.isBlank() || providedSecret == null)
            return false;
        return java.security.MessageDigest.isEqual(
                configuredSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                providedSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private String post(String baseUrl, String apiKey, String path, String jsonBody)
            throws Exception {
        var request =
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + path))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + apiKey)
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .build();
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        checkStatus(response);
        return response.body();
    }

    private String get(String baseUrl, String apiKey, String path) throws Exception {
        var request =
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + path))
                        .header("Authorization", "Bearer " + apiKey)
                        .GET()
                        .build();
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        checkStatus(response);
        return response.body();
    }

    private void checkStatus(HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException(
                    "Midjourney API HTTP " + response.statusCode() + ": " + response.body());
        }
    }
}
