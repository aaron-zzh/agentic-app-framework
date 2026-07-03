package com.xuejiai.aaf.framework.intelligent.ai.video;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.intelligent.ai.video.vo.HappyhorseParams;
import com.xuejiai.aaf.framework.intelligent.ai.video.vo.ImageToVideoRequest;
import com.xuejiai.aaf.framework.intelligent.ai.video.vo.ReferenceToVideoRequest;
import com.xuejiai.aaf.framework.intelligent.ai.video.vo.TextToVideoRequest;
import com.xuejiai.aaf.framework.intelligent.ai.video.vo.VideoEditApiRequest;
import com.xuejiai.aaf.framework.intelligent.ai.video.vo.VideoRequest;
import com.xuejiai.aaf.framework.intelligent.ai.video.vo.VideoTaskResult;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModel;

import lombok.extern.slf4j.Slf4j;

/**
 * 基于阿里云百炼 HappyHorse HTTP API 的视频生成实现。
 *
 * <p>支持模型（happyhorse 系列）：
 *
 * <ul>
 *   <li>happyhorse-1.1-t2v — 文生视频
 *   <li>happyhorse-1.1-i2v — 图生视频（首帧）
 *   <li>happyhorse-1.1-r2v — 参考生视频（多图）
 *   <li>happyhorse-1.0-video-edit — 视频编辑
 * </ul>
 *
 * <p>wan2.x 系列请使用 {@link WanxVideoGenerationService}。
 */
@Slf4j
@Service("dashScopeVideoGenerationService")
@ConditionalOnProperty(name = "spring.ai.dashscope.api-key", matchIfMissing = false)
public class DashScopeVideoGenerationService implements VideoGenerationService {

    private static final String SUBMIT_URL =
            "https://dashscope.aliyuncs.com/api/v1/services/aigc/video-generation/video-synthesis";
    private static final String QUERY_URL = "https://dashscope.aliyuncs.com/api/v1/tasks/";
    private static final String VIDEO_PATH =
            "/api/v1/services/aigc/video-generation/video-synthesis";

    private final String apiKey;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final com.xuejiai.aaf.framework.intelligent.core.model.AiModelRepository
            modelRepository;

    public DashScopeVideoGenerationService(
            @Value("${spring.ai.dashscope.api-key:}") String apiKey,
            com.xuejiai.aaf.framework.intelligent.core.model.AiModelRepository modelRepository) {
        this.apiKey = apiKey;
        this.modelRepository = modelRepository;
    }

    @Override
    public String submitTextToVideo(TextToVideoRequest request) {
        var model =
                request.getResolvedModel() != null
                        ? request.getResolvedModel()
                        : (request.getModel() != null
                                ? modelRepository
                                        .findByModelIdAndEnabledTrue(request.getModel())
                                        .orElse(null)
                                : null);
        var req =
                new VideoRequest(
                        request.getPrompt(),
                        null,
                        null,
                        model != null ? model.getModelId() : null,
                        request.getResolution(),
                        request.getRatio(),
                        request.getDuration(),
                        request.getSeed(),
                        null);
        var params =
                HappyhorseParams.of(
                        req, model != null ? model : placeholderModel("happyhorse-1.1-t2v"));
        return doSubmit(
                params.modelName(),
                params.toInput(),
                params.toParameters(),
                model != null ? model.effectiveBaseUrl() : null,
                model != null ? model.effectiveApiKey() : null);
    }

    @Override
    public String submitImageToVideo(ImageToVideoRequest request) {
        var aiModel =
                request.getModel() != null
                        ? modelRepository
                                .findByModelIdAndEnabledTrue(request.getModel())
                                .orElse(null)
                        : null;
        var req =
                new VideoRequest(
                        request.getPrompt(),
                        request.getFirstFrameUrl(),
                        null,
                        request.getModel(),
                        request.getResolution(),
                        null,
                        request.getDuration(),
                        request.getSeed(),
                        VideoRequest.ImageMode.FIRST_FRAME);
        var params =
                HappyhorseParams.of(
                        req,
                        aiModel != null
                                ? aiModel
                                : placeholderModel(
                                        request.getModel() != null
                                                ? request.getModel()
                                                : "happyhorse-1.1-i2v"));
        return doSubmit(
                params.modelName(),
                params.toInput(),
                params.toParameters(),
                aiModel != null ? aiModel.effectiveBaseUrl() : null,
                aiModel != null ? aiModel.effectiveApiKey() : null);
    }

    @Override
    public String submitReferenceToVideo(ReferenceToVideoRequest request) {
        var aiModel =
                request.getModel() != null
                        ? modelRepository
                                .findByModelIdAndEnabledTrue(request.getModel())
                                .orElse(null)
                        : null;
        var req =
                new VideoRequest(
                        request.getPrompt(),
                        null,
                        request.getReferenceImageUrls(),
                        request.getModel(),
                        request.getResolution(),
                        request.getRatio(),
                        request.getDuration(),
                        request.getSeed(),
                        VideoRequest.ImageMode.REFERENCE);
        var params =
                HappyhorseParams.of(
                        req,
                        aiModel != null
                                ? aiModel
                                : placeholderModel(
                                        request.getModel() != null
                                                ? request.getModel()
                                                : "happyhorse-1.1-r2v"));
        return doSubmit(
                params.modelName(),
                params.toInput(),
                params.toParameters(),
                aiModel != null ? aiModel.effectiveBaseUrl() : null,
                aiModel != null ? aiModel.effectiveApiKey() : null);
    }

    @Override
    public String submitVideoEdit(VideoEditApiRequest request) {
        var aiModel =
                request.getModel() != null
                        ? modelRepository
                                .findByModelIdAndEnabledTrue(request.getModel())
                                .orElse(null)
                        : null;
        var params =
                HappyhorseParams.ofEdit(
                        request,
                        aiModel != null
                                ? aiModel
                                : placeholderModel(
                                        request.getModel() != null
                                                ? request.getModel()
                                                : "happyhorse-1.0-video-edit"));
        return doSubmit(
                params.modelName(),
                params.toInput(),
                params.toParameters(),
                aiModel != null ? aiModel.effectiveBaseUrl() : null,
                aiModel != null ? aiModel.effectiveApiKey() : null);
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
            var root = JsonUtils.readTree(response.body());
            var output = root.get("output");

            var status = parseStatus(output.get("task_status").asString());
            var videoUrl = output.has("video_url") ? output.get("video_url").asString() : null;
            var origPrompt =
                    output.has("orig_prompt") ? output.get("orig_prompt").asString() : null;
            var submitTime =
                    output.has("submit_time") ? output.get("submit_time").asString() : null;
            var endTime = output.has("end_time") ? output.get("end_time").asString() : null;
            var errorMessage = output.has("message") ? output.get("message").asString() : null;

            Integer duration = null;
            String resolution = null;
            if (root.has("usage")) {
                var usage = root.get("usage");
                if (usage.has("duration")) duration = usage.get("duration").asInt();
                if (usage.has("SR")) resolution = usage.get("SR").asInt() + "p";
            }

            var result =
                    new VideoTaskResult(
                            taskId,
                            status,
                            videoUrl,
                            origPrompt,
                            submitTime,
                            endTime,
                            duration,
                            resolution);
            result.setErrorMessage(errorMessage);
            return result;
        } catch (Exception e) {
            log.error("[HappyHorse] 查询任务失败: taskId={}", taskId, e);
            return new VideoTaskResult(
                    taskId, VideoTaskResult.TaskStatus.UNKNOWN, null, null, null, null, null);
        }
    }

    // === 内部方法 ===

    private String doSubmit(
            String model, Map<String, Object> input, Map<String, Object> parameters) {
        return doSubmit(model, input, parameters, null, null);
    }

    private String doSubmit(
            String model,
            Map<String, Object> input,
            Map<String, Object> parameters,
            String baseUrl,
            String overrideApiKey) {
        try {
            var body = new HashMap<String, Object>();
            body.put("model", model);
            body.put("input", input);
            if (!parameters.isEmpty()) body.put("parameters", parameters);

            var json = JsonUtils.toJsonString(body);
            var submitUrl =
                    baseUrl != null
                            ? baseUrl.replaceAll("/$", "")
                                    + "/api/v1/services/aigc/video-generation/video-synthesis"
                            : SUBMIT_URL;
            var effectiveApiKey =
                    (overrideApiKey != null && !overrideApiKey.isBlank()) ? overrideApiKey : apiKey;
            var httpRequest =
                    HttpRequest.newBuilder()
                            .uri(URI.create(submitUrl))
                            .header("Content-Type", "application/json")
                            .header("Authorization", "Bearer " + effectiveApiKey)
                            .header("X-DashScope-Async", "enable")
                            .POST(HttpRequest.BodyPublishers.ofString(json))
                            .build();

            var response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            var root = JsonUtils.readTree(response.body());

            if (root.has("code")) {
                var errMsg = root.get("message").asString();
                throw new RuntimeException("视频生成任务提交失败: " + errMsg);
            }

            var taskId = root.get("output").get("task_id").asString();
            log.info("[HappyHorse] 任务提交成功: model={}, taskId={}", model, taskId);
            return taskId;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("视频生成任务提交失败: " + e.getMessage(), e);
        }
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

    /** 当调用方未传 resolvedModel 时，用模型名构建最小占位 AiModel（无 videoConfig，跳过校验）。 */
    private AiModel placeholderModel(String modelName) {
        var m = new AiModel();
        m.setModelName(modelName);
        return m;
    }
}
