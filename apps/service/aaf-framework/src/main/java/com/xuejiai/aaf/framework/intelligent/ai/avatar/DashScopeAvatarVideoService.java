package com.xuejiai.aaf.framework.intelligent.ai.avatar;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * 基于阿里云 wan2.2-s2v 的数字人视频生成实现。
 *
 * <p>检测接口（同步）：wan2.2-s2v-detect
 *
 * <p>生成接口（异步）：wan2.2-s2v，需携带 X-DashScope-Async: enable
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "spring.ai.dashscope.api-key", matchIfMissing = false)
public class DashScopeAvatarVideoService implements AvatarVideoService {

    private static final String DETECT_URL =
            "https://dashscope.aliyuncs.com/api/v1/services/aigc/image2video/image-detect/";
    private static final String SUBMIT_URL =
            "https://dashscope.aliyuncs.com/api/v1/services/aigc/image2video/video-synthesis/";
    private static final String QUERY_URL = "https://dashscope.aliyuncs.com/api/v1/tasks/";

    private static final String DETECT_MODEL = "wan2.2-s2v-detect";
    private static final String GENERATE_MODEL = "wan2.2-s2v";

    private final String apiKey;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DashScopeAvatarVideoService(@Value("${spring.ai.dashscope.api-key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public DetectResult detect(String imageUrl) {
        try {
            var body = Map.of("model", DETECT_MODEL, "input", Map.of("image_url", imageUrl));
            var root = post(DETECT_URL, body, false);

            var output = root.get("output");
            // detect 接口：task_status=SUCCEEDED 且 result=pass 表示通过
            var status = output.has("task_status") ? output.get("task_status").asText() : "";
            if (!"SUCCEEDED".equals(status)) {
                var msg = output.has("message") ? output.get("message").asText() : "检测未通过";
                return new DetectResult(false, msg);
            }
            // 部分版本返回 detect_result 字段
            if (output.has("detect_result")) {
                var pass = "pass".equalsIgnoreCase(output.get("detect_result").asText());
                var reason = pass ? null : output.path("message").asText("图片不符合要求");
                return new DetectResult(pass, reason);
            }
            return new DetectResult(true, null);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("图片检测失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String submit(SubmitRequest request) {
        try {
            var input =
                    Map.of(
                            "image_url", request.imageUrl(),
                            "audio_url", request.audioUrl());
            var parameters = new HashMap<String, Object>();
            parameters.put("style", request.style() != null ? request.style() : "speech");
            if (request.resolution() != null) {
                parameters.put("resolution", request.resolution());
            }

            var body =
                    Map.of(
                            "model", GENERATE_MODEL,
                            "input", input,
                            "parameters", parameters);

            var root = post(SUBMIT_URL, body, true);
            var taskId = root.get("output").get("task_id").asText();
            log.info("[AvatarVideo] 任务提交成功: taskId={}", taskId);
            return taskId;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("数字人视频任务提交失败: " + e.getMessage(), e);
        }
    }

    @Override
    public TaskResult query(String taskId) {
        try {
            var httpRequest =
                    HttpRequest.newBuilder()
                            .uri(URI.create(QUERY_URL + taskId))
                            .header("Authorization", "Bearer " + apiKey)
                            .GET()
                            .build();
            var response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            var root = objectMapper.readTree(response.body());
            var output = root.get("output");

            var status = parseStatus(output.get("task_status").asText());
            var videoUrl = output.has("video_url") ? output.get("video_url").asText() : null;
            var errorMsg = output.has("message") ? output.get("message").asText() : null;

            Integer duration = null;
            if (root.has("usage") && root.get("usage").has("video_duration")) {
                duration = root.get("usage").get("video_duration").asInt();
            }

            return new TaskResult(taskId, status, videoUrl, errorMsg, duration);
        } catch (Exception e) {
            log.error("[AvatarVideo] 查询任务失败: taskId={}", taskId, e);
            return new TaskResult(
                    taskId, TaskResult.TaskStatus.UNKNOWN, null, e.getMessage(), null);
        }
    }

    // ===== 内部方法 =====

    private com.fasterxml.jackson.databind.JsonNode post(String url, Object body, boolean async)
            throws Exception {
        var json = objectMapper.writeValueAsString(body);
        var builder =
                HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + apiKey);
        if (async) {
            builder.header("X-DashScope-Async", "enable");
        }
        var request = builder.POST(HttpRequest.BodyPublishers.ofString(json)).build();
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        var root = objectMapper.readTree(response.body());

        if (response.statusCode() != 200 || root.has("code")) {
            var msg = root.has("message") ? root.get("message").asText() : response.body();
            throw new RuntimeException("DashScope API 错误: " + msg);
        }
        return root;
    }

    private TaskResult.TaskStatus parseStatus(String s) {
        return switch (s) {
            case "PENDING" -> TaskResult.TaskStatus.PENDING;
            case "RUNNING" -> TaskResult.TaskStatus.RUNNING;
            case "SUCCEEDED" -> TaskResult.TaskStatus.SUCCEEDED;
            case "FAILED" -> TaskResult.TaskStatus.FAILED;
            default -> TaskResult.TaskStatus.UNKNOWN;
        };
    }
}
