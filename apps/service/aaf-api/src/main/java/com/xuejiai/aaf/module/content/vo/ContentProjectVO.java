package com.xuejiai.aaf.module.content.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 内容项目响应。
 *
 * @author AaronZZH & Kiro
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "内容项目信息")
public record ContentProjectVO(
        Long id,
        Integer version,
        String name,
        String projectTypeCode,
        String blueprintCode,
        String blueprintVersion,
        String domainExtensionCode,
        String domainExtensionVersion,
        String productionMode,
        String generationMode,
        String status,
        String brief,
        String coverUrl,
        List<String> channels,
        Integer graphRevision,
        Long primaryBrandProfileId,
        String primaryBrandProfileName,
        BigDecimal budgetLimit,
        BigDecimal costUsed,
        LocalDateTime lastActiveTime,
        LocalDateTime createTime,
        LocalDateTime updateTime) {}
