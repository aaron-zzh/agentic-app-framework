package com.xuejiai.aaf.module.ai.aigc.project.vo;

import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectDocumentReferenceType;

public record AigcProjectDocumentRefVO(
        Long id,
        Long objectId,
        AigcProjectDocumentReferenceType referenceType,
        Long documentId,
        Long fixedDocumentVersionId,
        Long effectiveDocumentVersionId,
        Long currentDocumentVersionId,
        Integer effectiveVersionNo,
        Integer currentVersionNo,
        String title,
        String role,
        Integer sortOrder,
        boolean outdated) {}
