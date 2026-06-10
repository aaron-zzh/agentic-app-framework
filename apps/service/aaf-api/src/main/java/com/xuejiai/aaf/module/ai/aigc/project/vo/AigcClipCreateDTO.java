package com.xuejiai.aaf.module.ai.aigc.project.vo;

/** 新增片段 DTO。 */
public record AigcClipCreateDTO(
        Long trackId,
        Long assetId,
        Long shotId,
        Long positionMs,
        Long inMs,
        Long outMs,
        String properties) {}
