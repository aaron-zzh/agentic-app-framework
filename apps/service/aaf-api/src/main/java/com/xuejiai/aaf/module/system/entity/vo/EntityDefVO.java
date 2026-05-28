package com.xuejiai.aaf.module.system.entity.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 实体定义响应。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "实体定义信息")
public record EntityDefVO(
        Long id,
        String slug,
        String config,
        Boolean builtin,
        Boolean enabled,
        LocalDateTime createTime,
        LocalDateTime updateTime) {}
