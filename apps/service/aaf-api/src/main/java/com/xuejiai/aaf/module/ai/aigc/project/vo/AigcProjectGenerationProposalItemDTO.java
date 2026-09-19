package com.xuejiai.aaf.module.ai.aigc.project.vo;

import java.util.List;

import com.xuejiai.aaf.module.ai.aigc.project.api.AigcGenerationProposalTargetType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 单个生成目标的客户端输入。
 *
 * <p>只接受业务目标与可编辑目标指令；项目版本、快照、graphRevision、指令 hash、item hash 与成本口径全部由服务端解析和冻结，
 * 客户端不得提交，避免伪造冻结范围或低报成本。
 */
public record AigcProjectGenerationProposalItemDTO(
        @NotNull AigcGenerationProposalTargetType targetType,
        @NotBlank String targetKey,
        String actionKey,
        @NotBlank String generationInstruction,
        List<@NotBlank String> dependencyKeys,
        List<@NotBlank String> requiredContextKeys,
        String modelPolicy,
        String confirmationPolicy) {}
