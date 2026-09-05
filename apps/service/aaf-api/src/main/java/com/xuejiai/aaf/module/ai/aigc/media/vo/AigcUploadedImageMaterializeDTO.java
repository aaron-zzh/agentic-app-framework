package com.xuejiai.aaf.module.ai.aigc.media.vo;

import jakarta.validation.constraints.NotNull;

/** 将当前用户上传图片物化为 AIGC 媒体版本的请求。 */
public record AigcUploadedImageMaterializeDTO(
        @NotNull Long fileId, String name, Long originalProjectId) {}
