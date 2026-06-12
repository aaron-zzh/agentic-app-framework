package com.xuejiai.aaf.framework.intelligent.ai.image;

import com.xuejiai.aaf.framework.intelligent.ai.image.vo.ImageEditRequest;
import com.xuejiai.aaf.framework.intelligent.ai.image.vo.ImageRequest;
import com.xuejiai.aaf.framework.intelligent.ai.image.vo.ImageResult;

/** 文生图服务接口，支持按模型动态切换（DALL-E / 通义万象 wanx 等）。 */
public interface ImageGenerationService {

    /**
     * 文生图。
     *
     * @param request 生成请求
     * @return 生成结果（URL 或 Base64）
     */
    ImageResult generate(ImageRequest request);

    /**
     * 图生图（参考图 + 风格 Prompt + 强度）。
     *
     * @param request 编辑请求（sourceUrl 必填，maskUrl 为 null）
     * @return 生成结果
     */
    ImageResult imageToImage(ImageEditRequest request);

    /**
     * 局部编辑（原图 + 蒙版区域 + 编辑 Prompt）。
     *
     * @param request 编辑请求（sourceUrl + maskUrl + prompt）
     * @return 生成结果
     */
    ImageResult editImage(ImageEditRequest request);

    /**
     * 图片生成请求。
     *
     * @param prompt 提示词
     * @param modelId ai_model 表中的 modelId
     * @param width 宽度（像素）
     * @param height 高度（像素）
     * @param responseFormat 返回格式：url / b64_json
     * @param negativePrompt 反向提示词（模型支持时生效）
     * @param seed 随机种子，0 表示不指定（模型支持时生效）
     * @param promptExtend 是否开启提示词智能改写（模型支持时生效）
     * @param count 生成张数，默认 1（模型支持时生效）
     */
}
