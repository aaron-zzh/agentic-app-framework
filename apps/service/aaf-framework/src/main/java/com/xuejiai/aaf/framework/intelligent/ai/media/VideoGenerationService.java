package com.xuejiai.aaf.framework.intelligent.ai.media;

import java.util.List;

/**
 * 视频生成服务接口，异步任务模式。
 *
 * <p>支持：文生视频(t2v)、图生视频(i2v)、视频编辑(video-edit)。
 * <p>基于阿里云百炼 HappyHorse 模型 HTTP API。
 */
public interface VideoGenerationService {

    /** 提交文生视频任务，返回 taskId。 */
    String submitTextToVideo(TextToVideoRequest request);

    /** 提交图生视频任务，返回 taskId。 */
    String submitImageToVideo(ImageToVideoRequest request);

    /** 提交视频编辑任务，返回 taskId。 */
    String submitVideoEdit(VideoEditApiRequest request);

    /** 查询任务结果。 */
    VideoTaskResult query(String taskId);

    // === 请求/响应 Records ===

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
            PENDING, RUNNING, SUCCEEDED, FAILED, CANCELED, UNKNOWN
        }
    }
}
