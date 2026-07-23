package com.xuejiai.aaf.framework.crud.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 资源的轻量展示引用，可用于选择器和关联读模型。 */
@Schema(description = "资源轻量引用")
public record ResourceRefDTO(
        @Schema(description = "资源标识") String resource,
        @Schema(description = "记录 ID") Long id,
        @Schema(description = "显示名称") String label,
        @Schema(description = "缩略图或头像地址") String imageUrl) {

    public ResourceRefDTO(Long id, String label, String imageUrl) {
        this(null, id, label, imageUrl);
    }

    public ResourceRefDTO withResource(String resource) {
        return new ResourceRefDTO(resource, id, label, imageUrl);
    }
}
