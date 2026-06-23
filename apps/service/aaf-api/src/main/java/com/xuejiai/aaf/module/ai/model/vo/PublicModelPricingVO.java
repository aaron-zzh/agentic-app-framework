package com.xuejiai.aaf.module.ai.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 用户侧模型公开定价 VO（积分价格，已乘加价倍率）。
 *
 * <p>各字段语义：
 *
 * <ul>
 *   <li>quotaType=0（TOKEN）：inputCreditPerK / outputCreditPerK 有值，表示每千 token 积分消耗
 *   <li>quotaType=1（PER_USE）：creditPerUse 有值，表示每次积分消耗
 *   <li>quotaType=2（PER_SEC）：creditPerSec 有值，表示每秒积分消耗
 *   <li>quotaType=3（PER_UNIT）：creditPerUnit 有值，表示每张/每单元积分消耗
 * </ul>
 */
@Schema(description = "用户侧模型公开定价")
public record PublicModelPricingVO(
        @Schema(description = "模型标识") String modelId,
        @Schema(description = "显示名称") String displayName,
        @Schema(description = "厂商") String provider,
        @Schema(description = "能力标签") String capabilities,
        @Schema(description = "计费类型：0=按量 1=按次 2=按秒 3=按单元") Short quotaType,
        @Schema(description = "输入积分/千token（quotaType=0）") Long inputCreditPerK,
        @Schema(description = "输出积分/千token（quotaType=0）") Long outputCreditPerK,
        @Schema(description = "积分/次（quotaType=1）") Long creditPerUse,
        @Schema(description = "积分/秒（quotaType=2）") Long creditPerSec,
        @Schema(description = "积分/单元（quotaType=3）") Long creditPerUnit,
        @Schema(description = "当前加价倍率") int markupRate) {}
