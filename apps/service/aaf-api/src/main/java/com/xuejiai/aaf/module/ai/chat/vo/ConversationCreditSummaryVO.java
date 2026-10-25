package com.xuejiai.aaf.module.ai.chat.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/** 对话 AI 实际结算积分汇总。 */
public record ConversationCreditSummaryVO(
        @Schema(description = "当前 run 实际消耗积分") long turnCredits,
        @Schema(description = "当前对话已关联 run 的累计实际消耗积分") long conversationCredits) {

    public ConversationCreditSummaryVO {
        if (turnCredits < 0 || conversationCredits < 0) {
            throw new IllegalArgumentException("对话积分汇总不能为负数");
        }
    }
}
