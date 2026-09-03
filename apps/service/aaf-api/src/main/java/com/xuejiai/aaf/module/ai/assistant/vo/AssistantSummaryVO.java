package com.xuejiai.aaf.module.ai.assistant.vo;

import java.util.List;

/** 当前用户可用的 Assistant 摘要（供角色/技能选择器展示，AAF-107 #10708）。 */
public record AssistantSummaryVO(
        String assistantId, String name, String defaultRoleKey, List<RoleSummaryVO> roles) {

    /** Assistant 下可选 Role 摘要。 */
    public record RoleSummaryVO(String roleKey, String name) {}
}
