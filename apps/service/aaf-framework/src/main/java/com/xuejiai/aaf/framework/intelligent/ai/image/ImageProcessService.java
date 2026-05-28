package com.xuejiai.aaf.framework.intelligent.ai.image;

import java.util.Map;

/**
 * 图像处理服务接口，支持多厂商策略（阿里云百炼 imageenhan 等）。
 *
 * <p>与文生图（{@link ImageGenerationService}）的区别： 文生图是 AI 生成能力（LLM ImageModel），图像处理是云服务工具能力（SDK 调用）。
 * 两者都归入 {@code ai/image/} 包，但接口分离。
 */
public interface ImageProcessService {

    /**
     * 处理图片（同步/异步取决于 method）。
     *
     * @param request 处理请求
     * @return 处理结果
     */
    ProcessResult process(ProcessRequest request);

    /**
     * 查询异步任务结果（卡通化等异步处理场景）。
     *
     * @param taskId 任务 ID（由 process 返回）
     * @return 处理结果
     */
    ProcessResult queryTask(String taskId);

    /**
     * 图像处理请求。
     *
     * @param imageUrl 原图 URL
     * @param method 处理方式：COLOR_ENHANCE / CARTOONIZE / AUTO_CROP
     * @param options 扩展参数（如 mode、effect、width、height）
     */
    record ProcessRequest(String imageUrl, String method, Map<String, String> options) {
        public ProcessRequest(String imageUrl, String method) {
            this(imageUrl, method, Map.of());
        }
    }

    /**
     * 图像处理结果。
     *
     * @param resultUrl 处理结果 URL（同步完成时有值）
     * @param taskId 异步任务 ID（异步处理时有值，需轮询 queryTask）
     * @param status SUCCESS / PENDING / FAILED
     * @param errorMessage 失败原因
     */
    record ProcessResult(String resultUrl, String taskId, String status, String errorMessage) {
        public static ProcessResult success(String url) {
            return new ProcessResult(url, null, "SUCCESS", null);
        }

        public static ProcessResult pending(String taskId) {
            return new ProcessResult(null, taskId, "PENDING", null);
        }

        public static ProcessResult failed(String error) {
            return new ProcessResult(null, null, "FAILED", error);
        }
    }
}
