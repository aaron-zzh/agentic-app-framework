package com.xuejiai.aaf.framework.intelligent.ai.model3d;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.util.JsonUtils;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;

/**
 * 基于阿里云百炼 Tripo 3D 模型生成实现。
 *
 * <p>API 文档：https://help.aliyun.com/zh/model-studio/tripo-3d-generation-guide
 *
 * <p>支持：文生3D、单图生3D、多图生3D（四视角：前/左/后/右）。
 *
 * @author AaronZZH & Kiro
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "spring.ai.dashscope.api-key", matchIfMissing = false)
public class TripoModel3dService implements Model3dGenerationService {

    private static final String SUBMIT_URL =
            "https://dashscope.aliyuncs.com/api/v1/services/aigc/video-generation/3d-generation";
    private static final String QUERY_URL = "https://dashscope.aliyuncs.com/api/v1/tasks/";
    private static final String DEFAULT_MODEL = "Tripo/Tripo-P1.0";

    private final String apiKey;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public TripoModel3dService(@Value("${spring.ai.dashscope.api-key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public String submitTextTo3d(TextTo3dRequest request) {
        var input = Map.<String, Object>of("prompt", request.prompt());
        var parameters = buildParameters(request.textureQuality(), request.pbr());
        return doSubmit(input, parameters);
    }

    @Override
    public String submitImageTo3d(ImageTo3dRequest request) {
        var input = Map.<String, Object>of("image", request.imageUrl());
        var parameters = buildParameters(request.textureQuality(), request.pbr());
        return doSubmit(input, parameters);
    }

    @Override
    public String submitMultiImageTo3d(MultiImageTo3dRequest request) {
        var imagesList = new ArrayList<Map<String, String>>();
        for (var img : request.images()) {
            if (img == null || img.fileToken() == null) {
                // 不需要的视角传空对象
                imagesList.add(Map.of());
            } else {
                imagesList.add(Map.of("type", img.type(), "file_token", img.fileToken()));
            }
        }
        var input = Map.<String, Object>of("images", imagesList);
        var parameters = buildParameters(request.textureQuality(), request.pbr());
        return doSubmit(input, parameters);
    }

    @Override
    public Model3dTaskResult query(String taskId) {
        try {
            var httpRequest =
                    HttpRequest.newBuilder()
                            .uri(URI.create(QUERY_URL + taskId))
                            .header("Authorization", "Bearer " + apiKey)
                            .GET()
                            .build();
            var response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            var root = JsonUtils.readTree(response.body());
            var output = root.get("output");

            var status = parseStatus(output.get("task_status").asString());

            String modelUrl = null;
            String baseModelUrl = null;
            String thumbnailUrl = null;
            String prompt = null;

            if (status == Model3dTaskResult.TaskStatus.SUCCEEDED && output.has("results")) {
                var results = output.get("results");
                if (results.isArray() && !results.isEmpty()) {
                    var first = results.get(0);
                    modelUrl = getTextOrNull(first, "pbr_model_url");
                    baseModelUrl = getTextOrNull(first, "base_model_url");
                    thumbnailUrl = getTextOrNull(first, "rendered_image_url");
                    prompt = getTextOrNull(first, "orig_prompt");
                }
            }

            return new Model3dTaskResult(
                    taskId, status, modelUrl, baseModelUrl, thumbnailUrl, prompt);
        } catch (Exception e) {
            log.error("[Tripo] 查询任务失败: taskId={}", taskId, e);
            return new Model3dTaskResult(
                    taskId, Model3dTaskResult.TaskStatus.FAILED, null, null, null, null);
        }
    }

    // ========== 内部方法 ==========

    private String doSubmit(Map<String, Object> input, Map<String, Object> parameters) {
        try {
            var body = new HashMap<String, Object>();
            body.put("model", DEFAULT_MODEL);
            body.put("input", input);
            if (!parameters.isEmpty()) body.put("parameters", parameters);

            var json = JsonUtils.toJsonString(body);
            var httpRequest =
                    HttpRequest.newBuilder()
                            .uri(URI.create(SUBMIT_URL))
                            .header("Content-Type", "application/json")
                            .header("Authorization", "Bearer " + apiKey)
                            .header("X-DashScope-Async", "enable")
                            .POST(HttpRequest.BodyPublishers.ofString(json))
                            .build();

            var response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            var root = JsonUtils.readTree(response.body());

            if (root.has("code")) {
                var errMsg = root.get("message").asString();
                throw new RuntimeException("Tripo 3D 任务提交失败: " + errMsg);
            }

            var taskId = root.get("output").get("task_id").asString();
            log.info("[Tripo] 3D 任务提交成功: taskId={}", taskId);
            return taskId;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Tripo 3D 任务提交失败: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> buildParameters(String textureQuality, Boolean pbr) {
        var params = new HashMap<String, Object>();
        if (textureQuality != null) {
            params.put("texture_quality", textureQuality);
        } else {
            params.put("texture_quality", "standard");
        }
        if (pbr != null && !pbr) {
            params.put("pbr", false);
            params.put("texture", false);
        }
        return params;
    }

    private Model3dTaskResult.TaskStatus parseStatus(String status) {
        return switch (status) {
            case "PENDING" -> Model3dTaskResult.TaskStatus.PENDING;
            case "RUNNING" -> Model3dTaskResult.TaskStatus.RUNNING;
            case "SUCCEEDED" -> Model3dTaskResult.TaskStatus.SUCCEEDED;
            default -> Model3dTaskResult.TaskStatus.FAILED;
        };
    }

    private String getTextOrNull(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asString() : null;
    }
}
