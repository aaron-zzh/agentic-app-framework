package com.xuejiai.aaf.framework.intelligent.ai.media;

/** 视频生成服务接口，异步任务模式。 */
public interface VideoGenerationService {

    /** 提交生成任务，返回 taskId */
    String submit(VideoGenerationRequest request);

    /** 查询任务结果 */
    VideoResult query(String taskId);

    record VideoGenerationRequest(String prompt, String negativePrompt, int durationSeconds) {}

    record VideoResult(String taskId, Status status, String videoUrl) {
        enum Status { PENDING, PROCESSING, SUCCEEDED, FAILED }
    }
}
