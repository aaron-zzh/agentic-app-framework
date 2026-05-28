package com.xuejiai.aaf.framework.intelligent.ai.video;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * 基于阿里云百炼 HappyHorse HTTP API 的视频生成实现。
 *
 * <p>支持模型：
 *
 * <ul>
 *   <li>happyhorse-1.0-t2v — 文生视频
 *   <li>happyhorse-1.0-i2v — 图生视频（首帧）
 *   <li>happyhorse-1.0-video-edit — 视频编辑
 * </ul>
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "spring.ai.dashscope.api-key", matchIfMissing = false)
public class DashScopeVideoGenerationService implements VideoGenerationService {

    private static final String SUBMIT_URL =
            "https://dashscope.aliyuncs.com/api/v1/services/aigc/video-generation/video-synthesis";
    private static final String QUERY_URL = "https://dashscope.aliyuncs.com/api/v1/tasks/";

    private final String apiKey;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DashScopeVideoGenerationService(
            @Value("${spring.ai.dashscope.api-key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public String submitTextToVideo(TextToVideoRequest request) {
        var model = request.model() != null ? request.model() : "happyhorse-1.0-t2v";
        var input = Map.<String, Object>of("prompt", request.prompt());
        var parameters = buildT2vParameters(request);
        return doSubmit(model, input, parameters);
    }

    @Override
    public String submitImageToVideo(ImageToVideoRequest request) {
        var model = request.model() != null ? request.model() : "happyhorse-1.0-i2v";
        var media = List.of(Map.of("type", "first_frame", "url", request.firstFrameUrl()));
        var input = new HashMap<String, Object>();
        if (request.prompt() != null) input.put("prompt", request.prompt());
        input.put("media", media);
        var parameters = buildI2vParameters(request);
        return doSubmit(model, input, parameters);
    }

    @Override
    public String submitVideoEdit(VideoEditApiRequest request) {
        var model = request.model() != null ? request.model() : "happyhorse-1.0-video-edit";
        var mediaList = new ArrayList<Map<String, String>>();
        mediaList.add(Map.of("type", "video", "url", request.videoUrl()));
        if (request.referenceImageUrls() != null) {
            for (var imgUrl : request.referenceImageUrls()) {
                mediaList.add(Map.of("type", "reference_image", "url", imgUrl));
            }
        }
        var input = Map.<String, Object>of("prompt", request.prompt(), "media", mediaList);
        var parameters = buildEditParameters(request);
        return doSubmit(model, input, parameters);
    }

    @Override
    public VideoTaskResult query(String taskId) {
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
            var origPrompt = output.has("orig_prompt") ? output.get("orig_prompt").asText() : null;
            var submitTime = output.has("submit_time") ? output.get("submit_time").asText() : null;
            var endTime = output.has("end_time") ? output.get("end_time").asText() : null;

            Integer duration = null;
            if (root.has("usage") && root.get("usage").has("duration")) {
                duration = root.get("usage").get("duration").asInt();
            }

            return new VideoTaskResult(
                    taskId, status, videoUrl, origPrompt, submitTime, endTime, duration);
        } catch (Exception e) {
            log.error("[HappyHorse] 查询任务失败: taskId={}", taskId, e);
            return new VideoTaskResult(
                    taskId, VideoTaskResult.TaskStatus.UNKNOWN, null, null, null, null, null);
        }
    }

    // === 内部方法 ===

    private String doSubmit(
            String model, Map<String, Object> input, Map<String, Object> parameters) {
        try {
            var body = new HashMap<String, Object>();
            body.put("model", model);
            body.put("input", input);
            if (!parameters.isEmpty()) body.put("parameters", parameters);

            var json = objectMapper.writeValueAsString(body);
            var httpRequest =
                    HttpRequest.newBuilder()
                            .uri(URI.create(SUBMIT_URL))
                            .header("Content-Type", "application/json")
                            .header("Authorization", "Bearer " + apiKey)
                            .header("X-DashScope-Async", "enable")
                            .POST(HttpRequest.BodyPublishers.ofString(json))
                            .build();

            var response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            var root = objectMapper.readTree(response.body());

            if (root.has("code")) {
                var errMsg = root.get("message").asText();
                throw new RuntimeException("视频生成任务提交失败: " + errMsg);
            }

            var taskId = root.get("output").get("task_id").asText();
            log.info("[HappyHorse] 任务提交成功: model={}, taskId={}", model, taskId);
            return taskId;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("视频生成任务提交失败: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> buildT2vParameters(TextToVideoRequest request) {
        var params = new HashMap<String, Object>();
        if (request.resolution() != null) params.put("resolution", request.resolution());
        if (request.ratio() != null) params.put("ratio", request.ratio());
        if (request.duration() != null) params.put("duration", request.duration());
        if (request.seed() != null) params.put("seed", request.seed());
        params.put("watermark", false);
        return params;
    }

    private Map<String, Object> buildI2vParameters(ImageToVideoRequest request) {
        var params = new HashMap<String, Object>();
        if (request.resolution() != null) params.put("resolution", request.resolution());
        if (request.duration() != null) params.put("duration", request.duration());
        if (request.seed() != null) params.put("seed", request.seed());
        params.put("watermark", false);
        return params;
    }

    private Map<String, Object> buildEditParameters(VideoEditApiRequest request) {
        var params = new HashMap<String, Object>();
        if (request.resolution() != null) params.put("resolution", request.resolution());
        if (request.audioSetting() != null) params.put("audio_setting", request.audioSetting());
        if (request.seed() != null) params.put("seed", request.seed());
        params.put("watermark", false);
        return params;
    }

    private VideoTaskResult.TaskStatus parseStatus(String status) {
        return switch (status) {
            case "PENDING" -> VideoTaskResult.TaskStatus.PENDING;
            case "RUNNING" -> VideoTaskResult.TaskStatus.RUNNING;
            case "SUCCEEDED" -> VideoTaskResult.TaskStatus.SUCCEEDED;
            case "FAILED" -> VideoTaskResult.TaskStatus.FAILED;
            case "CANCELED" -> VideoTaskResult.TaskStatus.CANCELED;
            default -> VideoTaskResult.TaskStatus.UNKNOWN;
        };
    }
}
