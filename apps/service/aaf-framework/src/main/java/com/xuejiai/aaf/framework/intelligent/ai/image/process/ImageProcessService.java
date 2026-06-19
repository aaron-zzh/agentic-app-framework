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

    ProcessResult queryTask(String taskId);

    record ProcessRequest(String imageUrl, String method, Map<String, String> options) {
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
