package com.xuejiai.aaf.framework.intelligent.ai.image.process;

import java.util.Map;

/**
 * 图像处理服务接口，支持多厂商策略（阿里云百炼 imageenhan 等）。
 *
 * <p>与文生图（{@link com.xuejiai.aaf.framework.intelligent.ai.image.ImageGenerationService}）的区别： 文生图是
 * AI 生成能力（LLM ImageModel），图像处理是云服务工具能力（SDK 调用）。
 */
public interface ImageProcessService {

    ProcessResult process(ProcessRequest request);

    /**
     * 查询异步任务结果。
     *
     * @param taskId 提交时返回的任务 ID（RequestId/JobId）
     * @param method 提交时的处理方式，用于路由到正确的类目客户端查询（不同类目的异步任务查询接口互相独立）
     */
    ProcessResult queryTask(String taskId, String method);

    record ProcessRequest(String imageUrl, String method, Map<String, String> options) {
        /**
         * @param imageUrl 图像输入串——可以是 {@code http(s)://} URL（含 OSS 签名 URL），也可以是 {@code
         *     data:<mime>;base64,<...>} Data URL（本地存储场景，避免暴露受权限保护的内部访问端点）。 具体识别与解析由各实现（如 {@link
         *     AliyunImageProcessService}）负责。
         */
        public ProcessRequest(String imageUrl, String method) {
            this(imageUrl, method, Map.of());
        }
    }

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
