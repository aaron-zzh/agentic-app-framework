package com.xuejiai.aaf.module.chat.livechat.seat.vo;

import java.time.LocalDateTime;

import com.xuejiai.aaf.common.enums.chat.SeatTypeEnum;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 坐席响应 VO。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "坐席信息")
public record SeatVO(
        @Schema(description = "ID") Long id,
        @Schema(description = "坐席类型") SeatTypeEnum seatType,
        @Schema(description = "关联用户 ID") Long userId,
        @Schema(description = "关联助手 ID") Long assistantId,
        @Schema(description = "昵称") String nickname,
        @Schema(description = "技能组") String skillGroup,
        @Schema(description = "状态") String status,
        @Schema(description = "当前会话数") Integer currentSessions,
        @Schema(description = "最大会话数") Integer maxSessions,
        @Schema(description = "创建时间") LocalDateTime createTime) {}
