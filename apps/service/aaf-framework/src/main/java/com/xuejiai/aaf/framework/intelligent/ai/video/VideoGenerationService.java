package com.xuejiai.aaf.framework.intelligent.ai.video;

import java.util.List;
import java.util.Map;

import com.xuejiai.aaf.framework.engine.credit.AiCreditGuard;
import com.xuejiai.aaf.framework.intelligent.core.AiCapability;
import com.xuejiai.aaf.framework.intelligent.core.AiUsage;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModel;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRoutingContext;

/**
 * 视频生成服务接口，异步任务模式。
 *
 * <p>支持：文生视频(t2v)、图生视频(i2v)、参考生视频(r2v)、视频编辑(video-edit)。
 *
 * <p>各实现按模型类型分发：happyhorse-* 走 HTTP API，wan2.* 走百炼 SDK。
 */
public interface VideoGenerationService extends AiCapability {

    @Override
    default String capability() {
        return CapabilityRoutingContext.CAP_VIDEO_GEN;
    }

    /**
     * 按单元估算积分预检费用（PER_UNIT）。
     *
     * <p>单价基准为 {@code model.modelPrice}，按分辨率乘以倍率系数：720p=1x，1080p=2x，4k=4x。 无法识别分辨率时退回 1x。
     */
    @Override
    default long estimateCost(AiModel model, Object req, int markupRate) {
        if (model == null || model.getModelPrice() == null) return 1;
        double resolutionMultiplier = 1.0;
        if (req instanceof VideoRequest vr && vr.resolution() != null) {
            resolutionMultiplier =
                    switch (vr.resolution().toLowerCase()) {
                        case "1080p" -> 2.0;
                        case "4k" -> 4.0;
                        default -> 1.0;
                    };
        }
        return Math.max(
                1,
                Math.round(
                        model.getModelPrice().doubleValue()
                                * resolutionMultiplier
                                * AiCreditGuard.YUAN_TO_CREDIT
                                * markupRate));
    }

    /**
     * 统一提交视频生成任务，返回 taskId。
     *
     * <ul>
     *   <li>有 referenceImageUrls → r2v（参考生视频）
     *   <li>有 imageUrl → i2v（图生视频）
     *   <li>否则 → t2v（文生视频）
     * </ul>
     */
    /**
     * 统一提交视频生成任务，返回 taskId。
     *
     * <p>路由完全基于业务意图（{@link VideoRequest#imageMode()}）：
     *
     * <ul>
     *   <li>{@code null} / {@code T2V} → 文生视频
     *   <li>{@code FIRST_FRAME} → 图生视频（i2v，imageUrl 为首帧）
     *   <li>{@code REFERENCE} → 参考生视频（r2v，优先取 referenceImageUrls，单张取 imageUrl）
     * </ul>
     */
    default String submit(VideoRequest request) {
        return switch (request.imageMode() != null
                ? request.imageMode()
                : VideoRequest.ImageMode.T2V) {
            case FIRST_FRAME ->
                    submitImageToVideo(
                            new ImageToVideoRequest(
                                    request.prompt(),
                                    request.imageUrl(),
                                    request.model(),
                                    request.resolution(),
                                    request.duration(),
                                    request.seed()));
            case REFERENCE -> {
                var refs =
                        request.referenceImageUrls() != null
                                        && !request.referenceImageUrls().isEmpty()
                                ? request.referenceImageUrls()
                                : List.of(request.imageUrl());
                yield submitReferenceToVideo(
                        new ReferenceToVideoRequest(
                                request.prompt(),
                                refs,
                                request.model(),
                                request.resolution(),
                                request.ratio(),
                                request.duration(),
                                request.seed()));
            }
            case T2V ->
                    submitTextToVideo(
                            new TextToVideoRequest(
                                    request.prompt(),
                                    null,
                                    request.resolution(),
                                    request.ratio(),
                                    request.duration(),
                                    request.seed(),
                                    null));
        };
    }

    /** 提交文生视频任务，返回 taskId。 */
    String submitTextToVideo(TextToVideoRequest request);

    /** 提交图生视频任务，返回 taskId。 */
    String submitImageToVideo(ImageToVideoRequest request);

    /** 提交参考生视频任务（多图 + prompt），返回 taskId。 */
    String submitReferenceToVideo(ReferenceToVideoRequest request);

    /** 提交视频编辑任务，返回 taskId。 */
    String submitVideoEdit(VideoEditApiRequest request);

    /** 查询任务结果。 */
    VideoTaskResult query(String taskId);

    // === 请求/响应 Records ===

    /**
     * 统一视频生成请求。
     *
     * <p>路由完全由 {@code imageMode} 决定，调用方必须显式传入意图，不依赖字段是否为空推断。
     */
    record VideoRequest(
            String prompt,
            /** 首帧图片 URL，传入则走 i2v。 */
            String imageUrl,
            /** 参考图片 URL 列表（1~9张），传入则走 r2v。prompt 中用 [Image 1] 等指代。 */
            List<String> referenceImageUrls,
            String model,
            String resolution,
            String ratio,
            Integer duration,
            Integer seed,
            /** 单张图片时的业务意图：T2V（默认，文生视频）、FIRST_FRAME（首帧，走 i2v）、REFERENCE（参考图，走 r2v）。 */
            ImageMode imageMode) {

        public enum ImageMode {
            T2V,
            FIRST_FRAME,
            REFERENCE
        }
    }

    /** 文生视频请求。 */
    record TextToVideoRequest(
            String prompt,
            /** 由 CapabilityRouter 解析后的模型，实现类从此取 modelName / apiKey 等。 */
            AiModel resolvedModel,
            String resolution,
            String ratio,
            Integer duration,
            Integer seed,
            /** 是否开启提示词扩写（wan2 系列支持）。 */
            Boolean promptExtend) {}

    /** 图生视频请求。 */
    record ImageToVideoRequest(
            String prompt,
            String firstFrameUrl,
            String model,
            String resolution,
            Integer duration,
            Integer seed) {}

    /** 参考生视频请求（多张参考图 + prompt，prompt 中用 [Image N] 指代）。 */
    record ReferenceToVideoRequest(
            String prompt,
            /** 参考图片 URL 列表，1~9 张。 */
            List<String> referenceImageUrls,
            String model,
            String resolution,
            String ratio,
            Integer duration,
            Integer seed) {}

    /** 视频编辑请求。 */
    record VideoEditApiRequest(
            String prompt,
            String videoUrl,
            List<String> referenceImageUrls,
            String model,
            String resolution,
            String audioSetting,
            Integer seed) {}

    /** 任务查询结果。 */
    record VideoTaskResult(
            String taskId,
            TaskStatus status,
            String videoUrl,
            String origPrompt,
            String submitTime,
            String endTime,
            Integer duration,
            /** 实际生成分辨率，如 "720p"/"1080p"，供 PER_UNIT 结算使用。 */
            String resolution)
            implements AiUsage {

        /** 兼容旧调用（无 resolution）。 */
        public VideoTaskResult(
                String taskId,
                TaskStatus status,
                String videoUrl,
                String origPrompt,
                String submitTime,
                String endTime,
                Integer duration) {
            this(taskId, status, videoUrl, origPrompt, submitTime, endTime, duration, null);
        }

        @Override
        public Map<String, Object> standardUsage() {
            var map = new java.util.HashMap<String, Object>();
            map.put("count", 1);
            if (duration != null) map.put("duration", duration);
            if (resolution != null) map.put("resolution", resolution);
            return map;
        }

        public enum TaskStatus {
            PENDING,
            RUNNING,
            SUCCEEDED,
            FAILED,
            CANCELED,
            UNKNOWN
        }
    }
}
