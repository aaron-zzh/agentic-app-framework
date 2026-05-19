package com.xuejiai.aaf.module.system.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/** 更新实体定义请求。 */
@Schema(description = "更新实体定义")
public record EntityDefUpdateDTO(String config, Boolean enabled) {}
