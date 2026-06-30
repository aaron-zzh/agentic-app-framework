package com.xuejiai.aaf.module.stats.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 积分消耗分类分布 VO。
 *
 * <p>饼图数据：按 credit_transaction.category 分组汇总消耗积分。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "积分消耗分类分布")
public record CreditsCategoryVO(
        @Schema(description = "分类数据列表") List<Item> items,
        @Schema(description = "合计消耗积分") long total) {

    @Schema(description = "单个分类数据项")
    public record Item(
            @Schema(description = "分类名称（来自 credit_transaction.category，如 AIGC/AI_CALL）")
                    String name,
            @Schema(description = "消耗积分") long value) {}
}
