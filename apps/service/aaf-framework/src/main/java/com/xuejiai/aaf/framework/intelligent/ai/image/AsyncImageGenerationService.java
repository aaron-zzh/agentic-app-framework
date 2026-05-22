package com.xuejiai.aaf.framework.intelligent.ai.image;

/**
 * 异步文生图服务接口，适用于需要提交任务后轮询结果的模型（如通义万象 wanx）。
 *
 * <p>与 {@link ImageGenerationService} 的区别：
 * <ul>
 *   <li>{@link ImageGenerationService} — 同步阻塞，适合 DALL-E 等直接返回结果的模型
 *   <li>本接口 — 异步任务，适合 wanx 等需要提交→轮询的模型
 * </ul>
 */
public interface AsyncImageGenerationService {

    /**
     * 提交文生图任务，立即返回 taskId。
     *
     * @param request 生成请求
     * @return taskId，用于后续轮询
     */
    String submitTask(AsyncImageRequest request);

    /**
     * 查询任务状态和结果。
     *
     * @param taskId 任务 ID
     * @return 任务结果
     */
    AsyncImageResult queryTask(String taskId);

    /**
     * 异步文生图请求。
     *
     * @param prompt   提示词
     * @param modelId  模型名称（如 qwen-image-plus、wanx-v1）
     * @param width    宽度（像素）
     * @param height   高度（像素）
     */
    record AsyncImageRequest(String prompt, String modelId, Integer width, Integer height) {
        public AsyncImageRequest(String prompt, String modelId) {
            this(prompt, modelId, 1024, 1024);
        }
    }

    /**
     * 异步文生图结果。
     *
     * @param taskId  任务 ID
     * @param status  任务状态：PENDING / RUNNING / SUCCEEDED / FAILED
     * @param imageUrl 图片 URL（status=SUCCEEDED 时有值）
     * @param errorMsg 错误信息（status=FAILED 时有值）
     */
    record AsyncImageResult(String taskId, String status, String imageUrl, String errorMsg) {
        public static AsyncImageResult pending(String taskId) {
            return new AsyncImageResult(taskId, "PENDING", null, null);
        }
        public static AsyncImageResult succeeded(String taskId, String imageUrl) {
            return new AsyncImageResult(taskId, "SUCCEEDED", imageUrl, null);
        }
        public static AsyncImageResult failed(String taskId, String errorMsg) {
            return new AsyncImageResult(taskId, "FAILED", null, errorMsg);
        }
    }
}
