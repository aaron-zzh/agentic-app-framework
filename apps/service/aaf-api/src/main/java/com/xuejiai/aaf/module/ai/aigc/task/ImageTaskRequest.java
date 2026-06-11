package com.xuejiai.aaf.module.ai.aigc.task;

import java.util.List;

/**
 * 图像生成任务请求参数。 封装所有生成参数，并提供 {@link #toParamsJson()} 序列化为 JSON 存入 aigc_task.params。
 *
 * @author AaronZZH & Kiro
 */
public record ImageTaskRequest(
        String prompt,
        String model,
        int width,
        int height,
        String negativePrompt,
        Integer seed,
        Boolean promptExtend,
        Integer imageCount,
        List<String> imageUrls,
        String quality,
        String format,
        String background,
        String contentModeration,
        String sizePreset,
        String aspectRatio,
        /** 用于素材命名/打标的用户原始输入（不含项目提示词前缀） */
        String displayPrompt,
        /** AI 模型显示名称，如 豆包图像生成，存入素材记录 */
        String modelName,
        /** 模型供应商编码，如 volcengine，存入素材记录 */
        String providerCode) {

    /** 序列化为 JSON 存入 aigc_task.params，供 AigcTaskExecutor 读取。 */
    public String toParamsJson() {
        var sb = new StringBuilder("{");
        sb.append("\"width\":").append(width).append(",\"height\":").append(height);
        if (negativePrompt != null)
            sb.append(",\"negativePrompt\":\"")
                    .append(negativePrompt.replace("\"", "'"))
                    .append("\"");
        if (seed != null && seed > 0) sb.append(",\"seed\":").append(seed);
        if (promptExtend != null) sb.append(",\"promptExtend\":").append(promptExtend);
        if (imageCount != null && imageCount > 1) sb.append(",\"imageCount\":").append(imageCount);
        if (imageUrls != null && !imageUrls.isEmpty()) {
            sb.append(",\"imageUrls\":[");
            for (int i = 0; i < imageUrls.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(imageUrls.get(i).replace("\"", "%22")).append("\"");
            }
            sb.append("]");
        }
        if (quality != null) sb.append(",\"quality\":\"").append(quality).append("\"");
        if (format != null) sb.append(",\"format\":\"").append(format).append("\"");
        if (background != null) sb.append(",\"background\":\"").append(background).append("\"");
        if (contentModeration != null)
            sb.append(",\"contentModeration\":\"").append(contentModeration).append("\"");
        if (sizePreset != null) sb.append(",\"sizePreset\":\"").append(sizePreset).append("\"");
        if (aspectRatio != null) sb.append(",\"aspectRatio\":\"").append(aspectRatio).append("\"");
        if (displayPrompt != null && !displayPrompt.isBlank())
            sb.append(",\"displayPrompt\":\"")
                    .append(displayPrompt.replace("\"", "'"))
                    .append("\"");
        sb.append("}");
        return sb.toString();
    }
}
