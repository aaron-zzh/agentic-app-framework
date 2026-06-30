package com.xuejiai.aaf.module.stats.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 积分消耗统计概览 VO。
 *
 * <p>面向 credits-analytics 仪表盘顶部三张卡片：余额 / 本月消耗 / 本月充值。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "积分消耗统计概览")
public record CreditsOverviewVO(
        @Schema(description = "当前可用余额（积分）") long balance,
        @Schema(description = "本月消耗积分") long monthConsumed,
        @Schema(description = "本月充值积分") long monthRecharged,
        @Schema(description = "本月消耗同比上月变化率（%），正数=增长") double consumedChangeRate,
        @Schema(description = "本月充值同比上月变化率（%），正数=增长") double rechargedChangeRate) {}
