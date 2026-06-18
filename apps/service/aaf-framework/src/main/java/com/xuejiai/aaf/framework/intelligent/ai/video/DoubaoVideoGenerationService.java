package com.xuejiai.aaf.framework.intelligent.ai.video;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * 火山引擎方舟 doubao-seedance 视频生成实现。
 *
 * <p>API 文档：<a href="https://www.volcengine.com/docs/82379/1399008">内容生成任务</a>
 *
 * <p>与 DashScope 的核心区别：请求体使用 {@code content} 数组，可混合 text / image_url / video_url / audio_url
 * 四种类型，支持参考图、参考视频、参考音频同时传入。
 *
 * <p>支持模型：doubao-seedance-2-0-260128 等 seedance 系列。
 */
@Slf4j
@Service
public class DoubaoVideoGenerationService implements VideoGenerationService {

    private static final String SUBMIT_URL =
            "https://ark.cn-beijing.volces.com/api/v3/contents/generations/tasks";
    private static final String QUERY_URL =
            "https://ark.cn-beijing.volces.com/api/v3/contents/generations/tasks/";

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ========== VideoGenerationService 标准接口适配 ==========

    @Override
    public String submitTextToVideo(TextToVideoRequest request) {
        var apiKey =
                request.resolvedModel() != null ? request.resolvedModel().effectiveApiKey() : null;
        var model =
                request.resolvedModel() != null
                        ? request.resolvedModel().getModelName()
                        : "doubao-seedance-2-0-260128";
        List<Map<String, Object>> content =
                List.of(Map.of("type", "text", "text", request.prompt()));
        return doSubmit(apiKey, model, content, request.ratio(), request.duration(), false);
    }

    @Override
    public String submitImageToVideo(ImageToVideoRequest request) {
        var apiKey = resolveApiKey(request.model());
        var model = request.model() != null ? request.model() : "doubao-seedance-2-0-260128";
        var content = new ArrayList<Map<String, Object>>();
        if (request.prompt() != null) content.add(Map.of("type", "text", "text", request.prompt()));
        content.add(
                Map.of(
                        "type",
                        "image_url",
                        "image_url",
                        Map.of("url", request.firstFrameUrl()),
                        "role",
                        "reference_image"));
        return doSubmit(apiKey, model, content, null, request.duration(), false);
    }

    @Override
    public String submitReferenceToVideo(ReferenceToVideoRequest request) {
        var apiKey = resolveApiKey(request.model());
        var model = request.model() != null ? request.model() : "doubao-seedance-2-0-260128";
        var content = new ArrayList<Map<String, Object>>();
        content.add(Map.of("type", "text", "text", request.prompt()));
        for (var imgUrl : request.referenceImageUrls()) {
            content.add(
                    Map.of(
                            "type",
                            "image_url",
                            "image_url",
                            Map.of("url", imgUrl),
                            "role",
                            "reference_image"));
        }
        return doSubmit(apiKey, model, content, request.ratio(), request.duration(), false);
    }

    @Override
    public String submitVideoEdit(VideoEditApiRequest request) {
        var apiKey = resolveApiKey(request.model());
        var model = request.model() != null ? request.model() : "doubao-seedance-2-0-260128";
        var content = new ArrayList<Map<String, Object>>();
        content.add(Map.of("type", "text", "text", request.prompt()));
        content.add(
                Map.of(
                        "type",
                        "video_url",
                        "video_url",
                        Map.of("url", request.videoUrl()),
                        "role",
                        "reference_video"));
        if (request.referenceImageUrls() != null) {
            for (var imgUrl : request.referenceImageUrls()) {
                content.add(
                        Map.of(
                                "type",
                                "image_url",
                                "image_url",
                                Map.of("url", imgUrl),
                                "role",
                                "reference_image"));
            }
        }
        return doSubmit(apiKey, model, content, null, null, false);
    }

    /**
     * 扩展提交：支持参考图、参考视频、参考音频同时传入（doubao-seedance 专属能力）。
     *
     * @param apiKey 火山方舟 API Key
     * @param model 模型名
     * @param prompt 文字描述
     * @param referenceImages 参考图 URL 列表（可为 null）
     * @param referenceVideos 参考视频 URL 列表（可为 null）
     * @param referenceAudios 参考音频 URL 列表（可为 null）
     * @param ratio 画面比例，如 "16:9"
     * @param duration 时长（秒）
     * @param generateAudio 是否生成配套音频
     */
    public String submitRich(
            String apiKey,
            String model,
            String prompt,
            List<String> referenceImages,
            List<String> referenceVideos,
            List<String> referenceAudios,
            String ratio,
            Integer duration,
            boolean generateAudio) {
        var content = new ArrayList<Map<String, Object>>();
        content.add(Map.of("type", "text", "text", prompt));
        if (referenceImages != null) {
            for (var url : referenceImages) {
                content.add(
                        Map.of(
                                "type",
                                "image_url",
                                "image_url",
                                Map.of("url", url),
                                "role",
                                "reference_image"));
            }
        }
        if (referenceVideos != null) {
            for (var url : referenceVideos) {
                content.add(
                        Map.of(
                                "type",
                                "video_url",
                                "video_url",
                                Map.of("url", url),
                                "role",
                                "reference_video"));
            }
        }
        if (referenceAudios != null) {
            for (var url : referenceAudios) {
                content.add(
                        Map.of(
                                "type",
                                "audio_url",
                                "audio_url",
                                Map.of("url", url),
                                "role",
                                "reference_audio"));
            }
        }
        return doSubmit(apiKey, model, content, ratio, duration, generateAudio);
    }

    @Override
    public VideoTaskResult query(String taskId) {
        // taskId 可能带 provider 前缀（如 "volcengine:xxx"），只取后半段
        var rawId = taskId.contains(":") ? taskId.substring(taskId.lastIndexOf(':') + 1) : taskId;
        // apiKey 查询时从任务 ID 无法获取，需调用方通过 AiModel 传入；
        // 此处暂不支持查询（doubao 任务查询需 apiKey，统一由 VideoGenerationController 处理）
        throw new UnsupportedOperationException(
                "doubao-seedance 任务查询需通过 DoubaoVideoGenerationService.queryWithKey(taskId, apiKey) 调用");
    }

    /** 查询任务状态（需显式传入 API Key）。 */
    public VideoTaskResult queryWithKey(String taskId, String apiKey) {
        try {
            var httpRequest =
                    HttpRequest.newBuilder()
                            .uri(URI.create(QUERY_URL + taskId))
                            .header("Authorization", "Bearer " + apiKey)
                            .GET()
                            .build();
            var response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            var root = objectMapper.readTree(response.body());

            var status = parseStatus(root.path("status").asText());
            var videoUrl = root.path("content").path(0).path("video_url").path("url").asText(null);
            return new VideoTaskResult(taskId, status, videoUrl, null, null, null, null);
        } catch (Exception e) {
            log.error("[doubao-seedance] 查询任务失败: taskId={}", taskId, e);
            return new VideoTaskResult(
                    taskId, VideoTaskResult.TaskStatus.UNKNOWN, null, null, null, null, null);
        }
    }

    // ========== 内部方法 ==========

    private String doSubmit(
            String apiKey,
            String model,
            List<? extends Map<String, Object>> content,
            String ratio,
            Integer duration,
            boolean generateAudio) {
        try {
            var body = new HashMap<String, Object>();
            body.put("model", model != null ? model : "doubao-seedance-2-0-260128");
            body.put("content", content);
            body.put("watermark", false);
            if (ratio != null) body.put("ratio", ratio);
            if (duration != null) body.put("duration", duration);
            if (generateAudio) body.put("generate_audio", true);

            var json = objectMapper.writeValueAsString(body);
            var httpRequest =
                    HttpRequest.newBuilder()
                            .uri(URI.create(SUBMIT_URL))
                            .header("Content-Type", "application/json")
                            .header("Authorization", "Bearer " + (apiKey != null ? apiKey : ""))
                            .POST(HttpRequest.BodyPublishers.ofString(json))
                            .build();

            var response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            var root = objectMapper.readTree(response.body());

            if (root.has("error")) {
                var errMsg = root.get("error").path("message").asText("未知错误");
                throw new RuntimeException("doubao-seedance 任务提交失败: " + errMsg);
            }

            var taskId = root.path("id").asText();
            log.info("[doubao-seedance] 任务提交成功: model={}, taskId={}", model, taskId);
            return taskId;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("doubao-seedance 任务提交失败: " + e.getMessage(), e);
        }
    }

    private VideoTaskResult.TaskStatus parseStatus(String status) {
        return switch (status != null ? status : "") {
            case "queued", "running" -> VideoTaskResult.TaskStatus.RUNNING;
            case "succeeded" -> VideoTaskResult.TaskStatus.SUCCEEDED;
            case "failed" -> VideoTaskResult.TaskStatus.FAILED;
            case "cancelled" -> VideoTaskResult.TaskStatus.CANCELED;
            default -> VideoTaskResult.TaskStatus.UNKNOWN;
        };
    }

    /** 标准接口 4 个方法通过 model 字符串无法取到 apiKey，返回 null 由调用方保证已在 AiModel 上配置。 */
    private String resolveApiKey(String modelId) {
        return null; // 实际 apiKey 由 VideoServiceFactory 传入 resolvedModel
    }
}
