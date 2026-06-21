package com.xuejiai.aaf.module.system.lead.vo;

import com.xuejiai.aaf.common.enums.lead.LeadChannelEnum;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 创建访客线索请求。
 *
 * <p>所有字段长度上限与 schema 对齐；按 channel 不同字段语义不同，由 service 层做语义校验：
 *
 * <ul>
 *   <li>CHAT —— 必填 {@code threadId} 与 {@code agentRole}
 *   <li>NEWSLETTER —— 必填 {@code email}
 *   <li>CONTACT —— 必填 {@code content}（可附 {@code name}/{@code email}/{@code phone}/{@code subject}）
 *   <li>FEEDBACK —— 必填 {@code content}（可附 {@code email}）
 * </ul>
 *
 * @author AaronZZH & Kiro
 */
public record GuestLeadCreateDTO(
        @Schema(
                        description = "访客匿名 ID（前端 localStorage UUID）",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank
                @Size(max = 64)
                String anonymousId,
        @Schema(description = "动作渠道", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
                LeadChannelEnum channel,
        @Schema(description = "邮箱") @Email @Size(max = 200) String email,
        @Schema(description = "姓名/昵称") @Size(max = 100) String name,
        @Schema(description = "电话") @Size(max = 50) String phone,
        @Schema(description = "主题") @Size(max = 200) String subject,
        @Schema(description = "内容/留言") String content,
        @Schema(description = "AG-UI threadId（CHAT 续聊用）") @Size(max = 64) String threadId,
        @Schema(description = "AgentScope 角色") @Size(max = 64) String agentRole) {}
