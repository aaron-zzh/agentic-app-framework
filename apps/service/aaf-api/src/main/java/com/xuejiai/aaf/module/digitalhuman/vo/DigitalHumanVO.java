package com.xuejiai.aaf.module.digitalhuman.vo;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/** 数字人响应。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "数字人")
public record DigitalHumanVO(
        @Schema(description = "数字人 ID") Long id,
        @Schema(description = "版本号，更新时作为 expectedVersion 回传") Integer version,
        @Schema(description = "名称") String name,
        @Schema(description = "描述") String description,
        @Schema(description = "头像图片 URL") String avatarImageUrl,
        @Schema(description = "标签") List<String> tags,
        @Schema(description = "形象信息") DigitalHumanAppearance appearance,
        @Schema(description = "音色配置") DigitalHumanVoiceConfig voiceConfig,
        @Schema(description = "模型配置") DigitalHumanModelConfig modelConfig,
        @Schema(description = "关联的助理 ID") Long assistantId,
        @Schema(description = "是否同步助理模型配置") Boolean syncModelConfig,
        @Schema(description = "生成视频数量") Integer videoCount,
        @Schema(description = "累计视频时长（秒）") Integer totalDuration,
        @Schema(description = "累计消耗积分") Integer creditsConsumed,
        @Schema(description = "最后使用时间") LocalDateTime lastUsedAt,
        @Schema(description = "状态：active / archived") String status,
        @Schema(description = "生成来源") String generationSource,
        @Schema(description = "关联的 AIGC 项目 ID") Long aigcProjectId,
        @Schema(description = "创建时间") LocalDateTime createTime,
        @Schema(description = "更新时间") LocalDateTime updateTime,
        @Schema(description = "当前用户是否为创建者；仅创建者可修改、删除或执行命令") Boolean ownedByCurrentUser) {

    /** 返回带有创建者标记的副本。 */
    public DigitalHumanVO withOwnedByCurrentUser(boolean owned) {
        return new DigitalHumanVO(
                id,
                version,
                name,
                description,
                avatarImageUrl,
                tags,
                appearance,
                voiceConfig,
                modelConfig,
                assistantId,
                syncModelConfig,
                videoCount,
                totalDuration,
                creditsConsumed,
                lastUsedAt,
                status,
                generationSource,
                aigcProjectId,
                createTime,
                updateTime,
                owned);
    }
}
