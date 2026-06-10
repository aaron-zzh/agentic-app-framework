package com.xuejiai.aaf.module.ai.aigc.project.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/** 更新分镜请求 DTO。 */
public record AigcShotUpdateDTO(
        @Schema(description = "镜号") Integer shotNo,
        @Schema(description = "镜头名称") String name,
        @Schema(description = "镜头描述") String description,
        @Schema(description = "对白/台词") String dialogue,
        @Schema(description = "扩展属性（JSON）") String properties) {}
