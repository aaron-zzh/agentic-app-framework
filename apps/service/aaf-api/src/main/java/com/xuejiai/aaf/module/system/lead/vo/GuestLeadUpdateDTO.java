package com.xuejiai.aaf.module.system.lead.vo;

import com.xuejiai.aaf.common.enums.lead.LeadStatusEnum;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 更新访客线索请求（管理端使用）。
 *
 * <p>仅允许变更管理类字段：处理状态、关联 contact、备注、处理人/时间。 内容字段（content/email 等）一旦录入不允许后端改动，避免破坏访客原始记录。
 *
 * @author AaronZZH & Kiro
 */
public record GuestLeadUpdateDTO(
        @Schema(description = "处理状态") LeadStatusEnum status,
        @Schema(description = "处理人 sys_user.id") Long handledBy,
        @Schema(description = "关联 contact ID（访客转正后填充）") Long contactId,
        @Schema(description = "备注") String remark) {}
