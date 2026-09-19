package com.xuejiai.aaf.module.ai.aigc.project.vo;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * 修订生成 Proposal 的请求；命中后产生新版本并使旧 hash 与已有确认失效。
 *
 * <p>来源类型保持首版不变，客户端不得改写；提交必须携带当前 {@code proposalVersion} 与 {@code proposalHash} 做乐观校验。
 */
public record AigcProjectGenerationProposalReviseDTO(
        @NotBlank String proposalId,
        @NotNull Integer proposalVersion,
        @NotBlank String proposalHash,
        @NotNull Integer expectedProjectVersion,
        String confirmationPolicy,
        LocalDateTime expiresAt,
        @NotEmpty List<@NotNull @Valid AigcProjectGenerationProposalItemDTO> items) {}
