package com.xuejiai.aaf.module.ai.aigc.project.vo;

import java.util.List;

import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectDocumentContextMode;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectDocumentReferenceType;
import com.xuejiai.aaf.module.document.api.DocumentVersionSemanticMetadataApi.SemanticTopic;

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
        String fieldKey,
        Integer instanceNo,
        Long selectedDocumentVersionId,
        String source,
        String contextNote,
        AigcProjectDocumentContextMode contextMode,
        String role,
        Integer sortOrder,
        boolean outdated,
        String summary,
        List<SemanticTopic> topics,
        String language,
        String semanticGenerationStatus,
        boolean semanticManualOverride,
        Integer semanticMetadataVersion) {
    public AigcProjectDocumentRefVO {
        topics = topics == null ? List.of() : List.copyOf(topics);
    }
}
