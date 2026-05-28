package com.xuejiai.aaf.framework.intelligent.ai.model3d;

/**
 * 3D 模型生成服务接口（异步任务模式）。
 *
 * @author AaronZZH & Kiro
 */
public interface Model3dGenerationService {

    /** 文生 3D，返回 taskId。 */
    String submitTextTo3d(TextTo3dRequest request);

    /** 图生 3D，返回 taskId。 */
    String submitImageTo3d(ImageTo3dRequest request);

    /** 查询任务结果。 */
    Model3dTaskResult query(String taskId);

    /** 文生 3D 请求。 */
    record TextTo3dRequest(String prompt, String style, String format) {}

    /** 图生 3D 请求。 */
    record ImageTo3dRequest(String imageUrl, String format) {}

    /** 任务查询结果。 */
    record Model3dTaskResult(
            String taskId,
            TaskStatus status,
            String modelUrl,
            String thumbnailUrl,
            String prompt) {

        public enum TaskStatus {
            PENDING,
            RUNNING,
            SUCCEEDED,
            FAILED
        }
    }
}
