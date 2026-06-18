package com.xuejiai.aaf.framework.intelligent.ai.avatar;

import com.xuejiai.aaf.framework.intelligent.core.AiCapability;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRoutingContext;

/**
 * 数字人视频生成服务接口（wan2.2-s2v）。
 *
 * <p>工作流：
 *
 * <ol>
 *   <li>{@link #detect} — 检测图片合规性（wan2.2-s2v-detect）
 *   <li>{@link #submit} — 提交视频生成异步任务（wan2.2-s2v）
 *   <li>{@link #query} — 轮询任务结果
 * </ol>
 */
public interface AvatarVideoService extends AiCapability {

    @Override
    default String capability() {
        return CapabilityRoutingContext.CAP_AVATAR;
    }

    /**
     * 检测图片是否满足数字人生成要求（清晰度、单人、正面等）。
     *
     * @param imageUrl 公网可访问的图片 URL
     * @return 检测结果
     */
    DetectResult detect(String imageUrl);

    /**
     * 提交数字人视频生成任务（异步）。
     *
     * @param request 生成请求
     * @return 第三方 task_id，用于后续轮询
     */
    String submit(SubmitRequest request);

    /**
     * 查询任务状态与结果。
     *
     * @param taskId 第三方 task_id
     * @return 任务结果
     */
    TaskResult query(String taskId);

    // ===== 数据模型 =====

    /** 图片检测结果。 */
    record DetectResult(
            /** 是否通过检测 */
            boolean passed,
            /** 失败原因，passed=true 时为 null */
            String reason) {}

    /** 视频生成提交请求。 */
    record SubmitRequest(
            /** 人物图片 URL */
            String imageUrl,
            /** 驱动音频 URL */
            String audioUrl,
            /** 风格：speech（说话）/ sing（唱歌）/ perform（表演） 默认 speech */
            String style,
            /** 分辨率：480P / 720P 默认 480P */
            String resolution) {}

    /** 任务查询结果。 */
    record TaskResult(
            String taskId,
            TaskStatus status,
            /** 生成的视频 URL，SUCCEEDED 时有值 */
            String videoUrl,
            /** 失败原因，FAILED 时有值 */
            String errorMessage,
            /** 视频时长（秒），SUCCEEDED 时有值 */
            Integer duration) {

        public enum TaskStatus {
            PENDING,
            RUNNING,
            SUCCEEDED,
            FAILED,
            UNKNOWN
        }
    }
}
