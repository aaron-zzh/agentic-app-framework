package com.xuejiai.aaf.framework.intelligent.ai.model3d;

import java.util.List;

import com.xuejiai.aaf.framework.intelligent.core.AiCapability;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRoutingContext;

/**
 * 3D 模型生成服务接口（异步任务模式）。
 *
 * @author AaronZZH & Kiro
 */
public interface Model3dGenerationService extends AiCapability {

    @Override
    default String capability() {
        return CapabilityRoutingContext.CAP_MODEL_3D;
    }

    /** 文生 3D，返回 taskId。 */
    String submitTextTo3d(TextTo3dRequest request);

    /** 单图生 3D，返回 taskId。 */
    String submitImageTo3d(ImageTo3dRequest request);

    /** 多图生 3D（前/左/后/右四视角），返回 taskId。 */
    String submitMultiImageTo3d(MultiImageTo3dRequest request);

    /** 查询任务结果。 */
    Model3dTaskResult query(String taskId);

    /** 文生 3D 请求。 */
    record TextTo3dRequest(
            String prompt,
            /** 贴图质量：standard / detailed */
            String textureQuality,
            /** 是否生成 PBR 材质，默认 true */
            Boolean pbr) {}

    /** 单图生 3D 请求。 */
    record ImageTo3dRequest(String imageUrl, String textureQuality, Boolean pbr) {}

    /** 多图生 3D 请求（四视角：前/左/后/右）。 */
    record MultiImageTo3dRequest(
            /** 四视角图片 URL 列表（前/左/后/右），不需要的视角传 null */
            List<ImageInput> images, String textureQuality, Boolean pbr) {}

    /** 多图输入项。 */
    record ImageInput(String type, String fileToken) {}

    /** 任务查询结果。 */
    record Model3dTaskResult(
            String taskId,
            TaskStatus status,
            /** PBR 材质模型 URL（GLB） */
            String modelUrl,
            /** 无贴图基础模型 URL */
            String baseModelUrl,
            /** 渲染预览图 URL */
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
