package com.xuejiai.aaf.module.ai.aigc.project.vo;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 项目物化时填充 Blueprint 初始化文档槽位的请求项。 */
public record AigcProjectDocumentInputDTO(
        @Size(max = 64) String fieldKey,
        @Positive Long documentId,
        @Positive Long documentVersionId,
        @Size(max = 500) String contextNote) {}
