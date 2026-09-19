package com.xuejiai.aaf.module.ai.aigc.project.vo;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * 表单路径创建待确认生成 Proposal 的请求。
 *
 * <p>不接受 {@code sourceType}/{@code sourceRef}：来源由服务端固定为用户表单，防止客户端伪造 Assistant 提议来源。
 */
public record AigcProjectGenerationProposalDTO(
        @NotNull Integer expectedProjectVersion,
        String confirmationPolicy,
        LocalDateTime expiresAt,
        @NotEmpty List<@NotNull @Valid AigcProjectGenerationProposalItemDTO> items) {}
