package com.xuejiai.aaf.module.system.dashboard.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 页面定义响应。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "页面定义信息")
public record PageDefVO(
        Long id,
        String slug,
        String title,
        String config,
        String status,
        Integer version,
        LocalDateTime publishedAt,
        LocalDateTime createTime,
        LocalDateTime updateTime) {}
