package com.xuejiai.aaf.module.ai.aigc.task.vo;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.xuejiai.aaf.common.util.JsonUtils;

/**
 * 视频生成任务请求参数。
 *
 * <p>封装所有生成参数，并提供 {@link #toParamsJson()} 序列化为 JSON 存入 aigc_task.params。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record VideoTaskRequest(
        String prompt,
        String model,
        Long projectId,
        String resolution,
        Integer duration,
        String ratio,
        Integer seed,
        /** 图片模式：T2V / FIRST_FRAME / REFERENCE */
        String imageMode,
        String imageUrl,
        List<String> referenceImageUrls,
        List<String> referenceVideoUrls,
        List<String> referenceAudioUrls,
        String audioSetting,
        Boolean promptExtend,
        Boolean generateAudio) {

    /** 序列化为 JSON 存入 aigc_task.params，供 AigcTaskExecutor 读取。 */
    public String toParamsJson() {
        return JsonUtils.toJsonString(this);
    }
}
