package com.xuejiai.aaf.framework.intelligent.ai.model3d;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * 基于 Meshy API 的 3D 模型生成实现。
 *
 * <p>API 文档：https://docs.meshy.ai
 *
 * @author AaronZZH & Kiro
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "aaf.ai.meshy.api-key", matchIfMissing = false)
public class MeshyModel3dService implements Model3dGenerationService {

    private static final String BASE_URL = "https://api.meshy.ai/openapi/v2";

    private final String apiKey;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MeshyModel3dService(@Value("${aaf.ai.meshy.api-key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public String submitTextTo3d(TextTo3dRequest request) {
        var body = new HashMap<String, Object>();
        body.put("mode", "preview");
        body.put("prompt", request.prompt());
        if (request.textureQuality() != null) body.put("texture_quality", request.textureQuality());
        return doSubmit("/text-to-3d", body);
    }

    @Override
    public String submitImageTo3d(ImageTo3dRequest request) {
        var body = new HashMap<String, Object>();
        body.put("image_url", request.imageUrl());
        return doSubmit("/image-to-3d", body);
    }

    @Override
    public String submitMultiImageTo3d(MultiImageTo3dRequest request) {
        // Meshy 不支持多图，取第一张有效图片走单图
        var firstImage = request.images().stream()
                .filter(img -> img != null && img.fileToken() != null)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("至少需要一张有效图片"));
        return submitImageTo3d(new ImageTo3dRequest(firstImage.fileToken(), request.textureQuality(), request.pbr()));
    }

    @Override
    public Model3dTaskResult query(String taskId) {
        // taskId 格式: "text-to-3d:{id}" 或 "image-to-3d:{id}"
        var parts = taskId.split(":", 2);
        var endpoint = parts.length == 2 ? parts[0] : "text-to-3d";
        var realId = parts.length == 2 ? parts[1] : taskId;

        try {
            var httpRequest =
                    HttpRequest.newBuilder()
                            .uri(URI.create(BASE_URL + "/" + endpoint + "/" + realId))
                            .header("Authorization", "Bearer " + apiKey)
                            .GET()
                            .build();
            var response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            var root = objectMapper.readTree(response.body());

            var status = parseStatus(root.get("status").asText());
            var modelUrl = root.has("model_urls") && root.get("model_urls").has("glb")
                    ? root.get("model_urls").get("glb").asText()
                    : null;
            var thumbnailUrl =
                    root.has("thumbnail_url") ? root.get("thumbnail_url").asText() : null;
            var prompt = root.has("prompt") ? root.get("prompt").asText() : null;

            return new Model3dTaskResult(taskId, status, modelUrl, null, thumbnailUrl, prompt);
        } catch (Exception e) {
            log.error("[Meshy] 查询任务失败: taskId={}", taskId, e);
            return new Model3dTaskResult(
                    taskId, Model3dTaskResult.TaskStatus.FAILED, null, null, null, null);
        }
    }

    // === 内部方法 ===

    private String doSubmit(String path, HashMap<String, Object> body) {
        try {
            var json = objectMapper.writeValueAsString(body);
            var httpRequest =
                    HttpRequest.newBuilder()
                            .uri(URI.create(BASE_URL + path))
                            .header("Content-Type", "application/json")
                            .header("Authorization", "Bearer " + apiKey)
                            .POST(HttpRequest.BodyPublishers.ofString(json))
                            .build();

            var response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            var root = objectMapper.readTree(response.body());

            if (root.has("result")) {
                var taskId = root.get("result").asText();
                log.info("[Meshy] 3D 任务提交成功: path={}, taskId={}", path, taskId);
                // 返回带前缀的 taskId，用于 query 时区分端点
                var prefix = path.substring(1); // 去掉前导 /
                return prefix + ":" + taskId;
            }

            throw new RuntimeException(
                    "3D 模型生成任务提交失败: " + response.body());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("3D 模型生成任务提交失败: " + e.getMessage(), e);
        }
    }

    private Model3dTaskResult.TaskStatus parseStatus(String status) {
        return switch (status) {
            case "PENDING" -> Model3dTaskResult.TaskStatus.PENDING;
            case "IN_PROGRESS" -> Model3dTaskResult.TaskStatus.RUNNING;
            case "SUCCEEDED" -> Model3dTaskResult.TaskStatus.SUCCEEDED;
            case "FAILED", "EXPIRED" -> Model3dTaskResult.TaskStatus.FAILED;
            default -> Model3dTaskResult.TaskStatus.PENDING;
        };
    }
}
