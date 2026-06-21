package com.xuejiai.aaf.module.system.lead.vo;

import java.time.LocalDateTime;

import com.xuejiai.aaf.common.enums.lead.LeadChannelEnum;
import com.xuejiai.aaf.common.enums.lead.LeadStatusEnum;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 访客线索响应。
 *
 * <p>注意：公开端点（{@code /api/public/leads/me}）不返回 {@code ipAddress}/{@code userAgent}/{@code referer}
 * 等敏感字段，由 controller 层做字段裁剪。
 *
 * @author AaronZZH & Kiro
 */
public record GuestLeadVO(
        @Schema(description = "主键 ID") Long id,
        @Schema(description = "访客匿名 ID") String anonymousId,
        @Schema(description = "动作渠道") LeadChannelEnum channel,
        @Schema(description = "邮箱") String email,
        @Schema(description = "姓名") String name,
        @Schema(description = "电话") String phone,
        @Schema(description = "主题") String subject,
        @Schema(description = "内容/留言") String content,
        @Schema(description = "AG-UI threadId（CHAT 续聊用）") String threadId,
        @Schema(description = "AgentScope 角色") String agentRole,
        @Schema(description = "最后消息时间") LocalDateTime lastMessageAt,
        @Schema(description = "来源 IP（仅管理端可见）") String ipAddress,
        @Schema(description = "User-Agent（仅管理端可见）") String userAgent,
        @Schema(description = "Referer（仅管理端可见）") String referer,
        @Schema(description = "IP 归属地（仅管理端可见）") String region,
        @Schema(description = "处理状态") LeadStatusEnum status,
        @Schema(description = "处理人 ID") Long handledBy,
        @Schema(description = "处理时间") LocalDateTime handledTime,
        @Schema(description = "关联 contact ID") Long contactId,
        @Schema(description = "创建时间") LocalDateTime createTime,
        @Schema(description = "更新时间") LocalDateTime updateTime) {}
