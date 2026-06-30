package com.xuejiai.aaf.module.ai.aigc.task.vo;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.xuejiai.aaf.common.util.JsonUtils;

/**
 * 图像生成任务请求参数。 封装所有生成参数，并提供 {@link #toParamsJson()} 序列化为 JSON 存入 aigc_task.params。
 *
 * @author AaronZZH & Kiro
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ImageTaskRequest(
        String prompt,
        String model,
        @JsonInclude(JsonInclude.Include.NON_DEFAULT) Integer width,
        @JsonInclude(JsonInclude.Include.NON_DEFAULT) Integer height,
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
        String providerCode,
        /** 所属项目 ID */
        Long projectId) {

    /** 序列化为 JSON 存入 aigc_task.params，供 AigcTaskExecutor 读取。width=null 时写入 autoSize=true。 */
    public String toParamsJson() {
        var node =
                (tools.jackson.databind.node.ObjectNode)
                        JsonUtils.readTree(JsonUtils.toJsonString(this));
        if (width == null || width == 0) {
            node.put("autoSize", true);
        }
        return JsonUtils.toJsonString(node);
    }
}
