package com.xuejiai.aaf.framework.intelligent.ai.video;

import java.util.List;

import com.xuejiai.aaf.framework.engine.credit.AiCreditGuard;
import com.xuejiai.aaf.framework.intelligent.ai.video.vo.ImageToVideoRequest;
import com.xuejiai.aaf.framework.intelligent.ai.video.vo.ReferenceToVideoRequest;
import com.xuejiai.aaf.framework.intelligent.ai.video.vo.TextToVideoRequest;
import com.xuejiai.aaf.framework.intelligent.ai.video.vo.VideoEditApiRequest;
import com.xuejiai.aaf.framework.intelligent.ai.video.vo.VideoRequest;
import com.xuejiai.aaf.framework.intelligent.ai.video.vo.VideoTaskResult;
import com.xuejiai.aaf.framework.intelligent.core.AiCapability;
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
     * 按 video_config.pricing 中的 pricePerSecond * duration 估算预检费用。
     *
     * <p>resolution 默认 "720p"，duration 默认 5s（最小档位保守估算）。 无 pricing 配置时退回 model.modelPrice 按次计费。
     */
    @Override
    default long estimateCost(AiModel model, Object req, int markupRate) {
        if (model == null) return 1;
        var vc = model.getVideoConfigParsed();
        String resolution = "720p";
        int duration = 5;
        if (req instanceof VideoRequest vr) {
            if (vr.getResolution() != null) resolution = vr.getResolution();
            if (vr.getDuration() != null) duration = vr.getDuration();
        }
        if (vc != null && vc.pricing() != null) {
            final String res = resolution;
            double pricePerSec =
                    vc.pricing().stream()
                            .filter(t -> res.equalsIgnoreCase(t.resolution()))
                            .mapToDouble(t -> t.pricePerSecond().doubleValue())
                            .findFirst()
                            .orElseGet(
                                    () ->
                                            vc.pricing().isEmpty()
                                                    ? 0
                                                    : vc.pricing()
                                                            .get(0)
                                                            .pricePerSecond()
                                                            .doubleValue());
            return Math.max(
                    1,
                    Math.round(pricePerSec * duration * AiCreditGuard.YUAN_TO_CREDIT * markupRate));
        }
        // 兜底：model.modelPrice 按次
        return AiCreditGuard.calcPerUseCost(model.getModelPrice(), markupRate);
    }

    /**
     * 统一提交视频生成任务，返回 taskId。
     *
     * <p>路由完全基于业务意图（{@link VideoRequest#getImageMode()}）：
     *
     * <ul>
     *   <li>{@code null} / {@code T2V} → 文生视频
     *   <li>{@code FIRST_FRAME} → 图生视频（i2v，imageUrl 为首帧）
     *   <li>{@code REFERENCE} → 参考生视频（r2v，优先取 referenceImageUrls，单张取 imageUrl）
     * </ul>
     */
    default String submit(VideoRequest request) {
        return switch (request.getImageMode() != null
                ? request.getImageMode()
                : VideoRequest.ImageMode.T2V) {
            case FIRST_FRAME ->
                    submitImageToVideo(
                            new ImageToVideoRequest(
                                    request.getPrompt(),
                                    request.getImageUrl(),
                                    request.getModel(),
                                    request.getResolution(),
                                    request.getDuration(),
                                    request.getSeed()));
            case REFERENCE -> {
                var refs =
                        request.getReferenceImageUrls() != null
                                        && !request.getReferenceImageUrls().isEmpty()
                                ? request.getReferenceImageUrls()
                                : List.of(request.getImageUrl());
                yield submitReferenceToVideo(
                        new ReferenceToVideoRequest(
                                request.getPrompt(),
                                refs,
                                request.getModel(),
                                request.getResolution(),
                                request.getRatio(),
                                request.getDuration(),
                                request.getSeed()));
            }
            case T2V ->
                    submitTextToVideo(
                            new TextToVideoRequest(
                                    request.getPrompt(),
                                    request.getModel(),
                                    request.getResolution(),
                                    request.getRatio(),
                                    request.getDuration(),
                                    request.getSeed(),
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
}
