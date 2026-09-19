package com.xuejiai.aaf.module.document.api;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 精确文档版本的派生语义元数据边界。 */
public interface DocumentVersionSemanticMetadataApi {

    Optional<SemanticMetadataView> find(Long documentVersionId);

    Map<Long, SemanticMetadataView> findByDocumentVersionIds(Collection<Long> documentVersionIds);

    SemanticMetadataView completeGenerated(CompleteGeneratedMetadataCommand command);

    SemanticMetadataView markGenerationFailed(MarkMetadataGenerationFailedCommand command);

    record SemanticTopic(String name, String normalizedName, Double confidence, String source) {}

    record SemanticMetadataView(
            Long documentVersionId,
            Integer metadataVersion,
            String summary,
            List<SemanticTopic> topics,
            String language,
            String generationStatus,
            String sourceType,
            String modelRef,
            boolean manualOverride,
            LocalDateTime generatedTime,
            Long updatedBy,
            String updatedByType,
            LocalDateTime updateTime) {
        public SemanticMetadataView {
            topics = topics == null ? List.of() : List.copyOf(topics);
        }
    }

    record CompleteGeneratedMetadataCommand(
            Long documentVersionId,
            Integer expectedMetadataVersion,
            String summary,
            List<SemanticTopic> topics,
            String language,
            String modelRef,
            boolean explicitRegeneration) {}

    record MarkMetadataGenerationFailedCommand(
            Long documentVersionId, Integer expectedMetadataVersion, String modelRef) {}
}
