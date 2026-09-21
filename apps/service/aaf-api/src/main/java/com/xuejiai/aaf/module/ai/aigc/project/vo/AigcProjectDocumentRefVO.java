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
        Integer semanticMetadataVersion,
        /**
         * 该文档最近一次 AI 生成执行的状态（{@code AigcExecutionRunStatus} 名称），从未生成过时为 {@code null}。
         *
         * <p>来源是执行侧按 itemKey 反查的最新 root Run，与触发方式无关；任务终结后仍保留，供文档列表就地显示生成中或生成失败。
         */
        String generationStatus) {
    public AigcProjectDocumentRefVO {
        topics = topics == null ? List.of() : List.copyOf(topics);
    }
}
