package com.xuejiai.aaf.module.document.service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.document.api.DocumentVersionSemanticMetadataApi;
import com.xuejiai.aaf.module.document.domain.DocumentVersionSemanticMetadata;
import com.xuejiai.aaf.module.document.event.DocumentVersionCreatedEvent;
import com.xuejiai.aaf.module.document.repository.DocumentVersionSemanticMetadataRepository;

import lombok.RequiredArgsConstructor;

/** 文档版本语义元数据服务；正文版本保持不可变，元数据通过独立 CAS 演进。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentVersionSemanticMetadataService implements DocumentVersionSemanticMetadataApi {

    private final DocumentVersionSemanticMetadataRepository repository;
    private final OperatorContext operatorContext;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void initializePending(DocumentVersionCreatedEvent event) {
        if (repository.findByDocumentVersionId(event.documentVersionId()).isPresent()) {
            return;
        }
        var metadata = new DocumentVersionSemanticMetadata();
        metadata.setDocumentVersionId(event.documentVersionId());
        metadata.setOwnerId(event.ownerId());
        metadata.setOrgId(event.orgId());
        metadata.setWorkspaceId(event.workspaceId());
        metadata.setSourceType("AIGC".equalsIgnoreCase(event.sourceType()) ? "AI" : "MANUAL");
        metadata.setCreateByType(operatorContext.currentOperatorType().name());
        repository.save(metadata);
    }

    @Override
    public Optional<SemanticMetadataView> find(Long documentVersionId) {
        if (documentVersionId == null) {
            return Optional.empty();
        }
        return repository.findByDocumentVersionId(documentVersionId).map(this::toView);
    }

    @Override
    public Map<Long, SemanticMetadataView> findByDocumentVersionIds(
            Collection<Long> documentVersionIds) {
        if (documentVersionIds == null || documentVersionIds.isEmpty()) {
            return Map.of();
        }
        var ids = new LinkedHashSet<>(documentVersionIds);
        ids.remove(null);
        if (ids.isEmpty()) {
            return Map.of();
        }
        return repository.findAllByDocumentVersionIdIn(ids).stream()
                .collect(
                        Collectors.toUnmodifiableMap(
                                DocumentVersionSemanticMetadata::getDocumentVersionId,
                                this::toView));
    }

    @Override
    @Transactional
    public SemanticMetadataView completeGenerated(CompleteGeneratedMetadataCommand command) {
        requireCommand(command);
        var metadata =
                requireLocked(command.documentVersionId(), command.expectedMetadataVersion());
        var preserveManual =
                Boolean.TRUE.equals(metadata.getManualOverride())
                        && !command.explicitRegeneration();
        if (!preserveManual || isBlank(metadata.getSummary())) {
            metadata.setSummary(normalize(command.summary()));
        }
        if (!preserveManual || metadata.getTopics() == null || metadata.getTopics().isEmpty()) {
            metadata.setTopics(normalizeTopics(command.topics(), "AI"));
        }
        if (!preserveManual || isBlank(metadata.getLanguage())) {
            metadata.setLanguage(normalize(command.language()));
        }
        metadata.setGenerationStatus("READY");
        metadata.setSourceType(preserveManual ? "HYBRID" : "AI");
        metadata.setModelRef(normalize(command.modelRef()));
        metadata.setGeneratedTime(LocalDateTime.now());
        if (command.explicitRegeneration()) {
            metadata.setManualOverride(false);
        }
        incrementVersion(metadata);
        return toView(repository.save(metadata));
    }

    @Override
    @Transactional
    public SemanticMetadataView markGenerationFailed(MarkMetadataGenerationFailedCommand command) {
        if (command == null || command.documentVersionId() == null) {
            throw badRequest("语义元数据失败命令不完整");
        }
        var metadata =
                requireLocked(command.documentVersionId(), command.expectedMetadataVersion());
        if (!Boolean.TRUE.equals(metadata.getManualOverride())) {
            metadata.setGenerationStatus("FAILED");
            metadata.setModelRef(normalize(command.modelRef()));
            incrementVersion(metadata);
            repository.save(metadata);
        }
        return toView(metadata);
    }

    @Transactional
    public SemanticMetadataView updateManual(
            Long documentVersionId,
            Integer expectedMetadataVersion,
            String summary,
            List<SemanticTopic> topics,
            String language) {
        var metadata = requireLocked(documentVersionId, expectedMetadataVersion);
        var hadGeneratedContent =
                "AI".equals(metadata.getSourceType())
                        && "READY".equals(metadata.getGenerationStatus());
        metadata.setSummary(normalize(summary));
        metadata.setTopics(normalizeTopics(topics, "MANUAL"));
        metadata.setLanguage(normalize(language));
        metadata.setGenerationStatus("READY");
        metadata.setSourceType(hadGeneratedContent ? "HYBRID" : "MANUAL");
        metadata.setManualOverride(true);
        incrementVersion(metadata);
        return toView(repository.save(metadata));
    }

    private DocumentVersionSemanticMetadata requireLocked(
            Long documentVersionId, Integer expectedMetadataVersion) {
        if (documentVersionId == null || expectedMetadataVersion == null) {
            throw badRequest("documentVersionId 和 expectedMetadataVersion 不能为空");
        }
        var metadata =
                repository
                        .findLockedByDocumentVersionId(documentVersionId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.NOT_FOUND, "文档版本语义元数据不存在"));
        if (!Objects.equals(metadata.getVersion(), expectedMetadataVersion)) {
            throw new BusinessException(409, "语义元数据已被其他操作更新，请刷新后重试");
        }
        return metadata;
    }

    private void requireCommand(CompleteGeneratedMetadataCommand command) {
        if (command == null || command.documentVersionId() == null) {
            throw badRequest("语义元数据生成结果不完整");
        }
    }

    private List<SemanticTopic> normalizeTopics(List<SemanticTopic> topics, String defaultSource) {
        if (topics == null || topics.isEmpty()) {
            return List.of();
        }
        return topics.stream()
                .filter(Objects::nonNull)
                .map(
                        topic -> {
                            var name = normalize(topic.name());
                            if (name == null) {
                                throw badRequest("主题名称不能为空");
                            }
                            return new SemanticTopic(
                                    name,
                                    normalize(topic.normalizedName()),
                                    topic.confidence(),
                                    isBlank(topic.source())
                                            ? defaultSource
                                            : topic.source().trim().toUpperCase());
                        })
                .toList();
    }

    private void incrementVersion(DocumentVersionSemanticMetadata metadata) {
        metadata.setVersion(metadata.getVersion() == null ? 1 : metadata.getVersion() + 1);
        metadata.setUpdateByType(operatorContext.currentOperatorType().name());
    }

    private SemanticMetadataView toView(DocumentVersionSemanticMetadata metadata) {
        return new SemanticMetadataView(
                metadata.getDocumentVersionId(),
                metadata.getVersion(),
                metadata.getSummary(),
                metadata.getTopics(),
                metadata.getLanguage(),
                metadata.getGenerationStatus(),
                metadata.getSourceType(),
                metadata.getModelRef(),
                Boolean.TRUE.equals(metadata.getManualOverride()),
                metadata.getGeneratedTime(),
                metadata.getUpdateBy(),
                metadata.getUpdateByType(),
                metadata.getUpdateTime());
    }

    private String normalize(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(GlobalErrorCode.BAD_REQUEST, message);
    }
}
