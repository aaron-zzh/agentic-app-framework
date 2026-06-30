package com.xuejiai.aaf.module.stats.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 积分流水记录 VO（管理员视角，含用户信息）。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "积分流水记录（管理员视角）")
public record CreditRecordVO(
        @Schema(description = "流水 ID") Long id,
        @Schema(description = "用户昵称") String userName,
        @Schema(description = "脱敏手机号，格式 138****8888") String phone,
        @Schema(description = "流水类型：EARN/SPEND/FREEZE/UNFREEZE/EXPIRE") String type,
        @Schema(description = "消费分类（仅 SPEND 有值，如 AIGC/AI_CALL）") String category,
        @Schema(description = "积分变动量（正数=收入，负数=支出，取绝对值存储）") long amount,
        @Schema(description = "变动后余额") long balanceAfter,
        @Schema(description = "来源描述") String source,
        @Schema(description = "备注") String remark,
        @Schema(description = "创建时间") LocalDateTime createTime) {}
