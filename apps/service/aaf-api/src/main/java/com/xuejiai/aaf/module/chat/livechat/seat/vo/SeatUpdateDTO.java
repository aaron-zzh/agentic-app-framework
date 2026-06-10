package com.xuejiai.aaf.module.chat.livechat.seat.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 更新坐席请求。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "更新坐席请求")
public record SeatUpdateDTO(
        @Schema(description = "昵称") String nickname,
        @Schema(description = "技能组") String skillGroup,
        @Schema(description = "状态") String status,
        @Schema(description = "最大会话数") Integer maxSessions) {}
