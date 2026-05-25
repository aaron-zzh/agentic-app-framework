package com.xuejiai.aaf.module.aigc.vo;

import jakarta.validation.constraints.NotBlank;

/** 素材标签创建请求。 */
public record MediaTagCreateDTO(@NotBlank String name, String color) {}
