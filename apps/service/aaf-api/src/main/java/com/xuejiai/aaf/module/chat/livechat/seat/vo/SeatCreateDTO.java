package com.xuejiai.aaf.module.chat.livechat.seat.vo;

import com.xuejiai.aaf.module.chat.enums.SeatType;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 创建坐席请求。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "创建坐席请求")
public record SeatCreateDTO(
        @NotNull @Schema(description = "坐席类型") SeatType seatType,
        @Schema(description = "关联用户 ID") Long userId,
        @Schema(description = "关联助手 ID") Long assistantId,
        @Schema(description = "昵称") String nickname,
        @Schema(description = "技能组") String skillGroup,
        @Schema(description = "最大会话数") Integer maxSessions) {}
