package com.xuejiai.aaf.framework.intelligent.ai.video;

import java.util.List;

/**
 * 视频生成服务接口，异步任务模式。
 *
 * <p>支持：文生视频(t2v)、图生视频(i2v)、参考生视频(r2v)、视频编辑(video-edit)。
 *
 * <p>基于阿里云百炼 HappyHorse 模型 HTTP API。
 */
public interface VideoGenerationService {

    /**
     * 统一提交视频生成任务，返回 taskId。
     *
     * <ul>
     *   <li>有 referenceImageUrls → r2v（参考生视频）
     *   <li>有 imageUrl → i2v（图生视频）
     *   <li>否则 → t2v（文生视频）
     * </ul>
     */
    default String submit(VideoRequest request) {
        if (request.referenceImageUrls() != null && !request.referenceImageUrls().isEmpty()) {
            return submitReferenceToVideo(
                    new ReferenceToVideoRequest(
                            request.prompt(),
                            request.referenceImageUrls(),
                            request.model(),
                            request.resolution(),
                            request.ratio(),
                            request.duration(),
                            request.seed()));
        }
        if (request.imageUrl() != null) {
            return submitImageToVideo(
                    new ImageToVideoRequest(
                            request.prompt(),
                            request.imageUrl(),
                            request.model(),
                            request.resolution(),
                            request.duration(),
                            request.seed()));
        }
        return submitTextToVideo(
                new TextToVideoRequest(
                        request.prompt(),
                        request.model(),
                        request.resolution(),
                        request.ratio(),
                        request.duration(),
                        request.seed()));
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
     * <ul>
     *   <li>有 referenceImageUrls → 参考生视频（r2v）
     *   <li>有 imageUrl → 图生视频（i2v）
     *   <li>否则 → 文生视频（t2v）
     * </ul>
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
            Integer seed) {}

    /** 文生视频请求。 */
    record TextToVideoRequest(
            String prompt,
            String model,
            String resolution,
            String ratio,
            Integer duration,
            Integer seed) {}

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
            Integer duration) {

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
