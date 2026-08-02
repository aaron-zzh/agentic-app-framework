package com.xuejiai.aaf.module.knowledge.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.xuejiai.aaf.common.enums.knowledge.DocumentStatusEnum;
import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.crud.definition.CrudOperation;
import com.xuejiai.aaf.framework.crud.enforcement.AccessMode;
import com.xuejiai.aaf.framework.engine.knowledge.graph.GraphService;
import com.xuejiai.aaf.framework.engine.knowledge.pipeline.KnowledgePipelineService;
import com.xuejiai.aaf.framework.engine.knowledge.rag.HybridSearchService;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeGraphProjectionService;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeSearchContracts.AuthorizedQuery;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeSearchContracts.ChannelWeights;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeSearchContracts.Response;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.TrustedKnowledgeStore;
import com.xuejiai.aaf.framework.engine.meta.runtime.TaskExecutionInProgressException;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationSubject;
import com.xuejiai.aaf.module.knowledge.domain.KnowledgeBase;
import com.xuejiai.aaf.module.knowledge.domain.KnowledgeDocument;
import com.xuejiai.aaf.module.knowledge.repository.KnowledgeBaseRepository;
import com.xuejiai.aaf.module.knowledge.repository.KnowledgeDocumentRepository;
import com.xuejiai.aaf.module.knowledge.vo.BatchImportProgressVO;
import com.xuejiai.aaf.module.knowledge.vo.CreateKnowledgeBaseRequest;
import com.xuejiai.aaf.module.knowledge.vo.KnowledgeBaseMaintenancePageDTO;
import com.xuejiai.aaf.module.knowledge.vo.KnowledgeBaseMaintenanceVO;
import com.xuejiai.aaf.module.knowledge.vo.KnowledgeBaseStatsVO;
import com.xuejiai.aaf.module.knowledge.vo.KnowledgeBaseUpdateDTO;
import com.xuejiai.aaf.module.knowledge.vo.KnowledgeBaseVO;
import com.xuejiai.aaf.module.knowledge.vo.KnowledgeDocumentVO;
import com.xuejiai.aaf.module.knowledge.vo.KnowledgeGraphVO;
import com.xuejiai.aaf.module.knowledge.vo.KnowledgeGraphVO.GraphProjectionStatusVO;
import com.xuejiai.aaf.module.knowledge.vo.KnowledgeSearchDTO;
import com.xuejiai.aaf.module.knowledge.vo.KnowledgeSearchResponseVO;
import com.xuejiai.aaf.module.knowledge.vo.KnowledgeSearchResponseVO.SearchResultItemVO;
import com.xuejiai.aaf.module.knowledge.vo.KnowledgeSearchResponseVO.SourceVO;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

/** 知识库管理服务；标准管理入口统一复用 BaseCrud 安全与租户管线。 */
@Service
@RequiredArgsConstructor
public class KnowledgeBaseService
        extends BaseCrudService<
                KnowledgeBase,
                KnowledgeBaseVO,
                CreateKnowledgeBaseRequest,
                KnowledgeBaseUpdateDTO,
                PageParam> {

    private static final Set<String> MAINTENANCE_SORT_FIELDS =
            Set.of(
                    "id",
                    "name",
                    "visibility",
                    "status",
                    "ownerId",
                    "orgId",
                    "workspaceId",
                    "createTime",
                    "updateTime");

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final KnowledgeDocumentUploadService uploadService;
    private final KnowledgeDocumentQueueService queueService;
    private final KnowledgeDocumentExecutionLeaseService executionLeaseService;
    private final HybridSearchService hybridSearchService;
    private final GraphService graphService;
    private final KnowledgeGraphProjectionService graphProjectionService;
    private final TrustedKnowledgeStore truthStore;
    private final KnowledgePipelineService pipelineService;
    private final KnowledgeDocumentCleanupQueueService cleanupQueueService;
    private final EntityManager entityManager;
    private final OperatorContext operatorContext;

    @Override
    protected KnowledgeBaseRepository getRepository() {
        return knowledgeBaseRepository;
    }

    @Override
    protected KnowledgeBaseVO toVO(KnowledgeBase entity) {
        return new KnowledgeBaseVO(
                entity.getId(),
                entity.getStableId(),
                entity.getName(),
                entity.getDescription(),
                entity.getVisibility(),
                entity.getScopeCode(),
                entity.getEmbeddingModel(),
                Objects.requireNonNullElse(entity.getChunkStrategy(), "recursive"),
                Objects.requireNonNullElse(entity.getChunkSize(), 512),
                Objects.requireNonNullElse(entity.getChunkOverlap(), 64),
                Objects.requireNonNullElse(entity.getDocumentCount(), 0L),
                entity.getStatus(),
                entity.getCreateTime(),
                entity.getUpdateTime());
    }

    @Override
    protected KnowledgeBase toEntity(CreateKnowledgeBaseRequest request) {
        var entity = new KnowledgeBase();
        entity.setName(requireName(request.name()));
        entity.setDescription(request.description());
        entity.setVisibility(Objects.requireNonNullElse(request.visibility(), "PRIVATE"));
        entity.setScopeCode(request.scopeCode());
        entity.setEmbeddingModel(request.embeddingModel());
        entity.setChunkStrategy(Objects.requireNonNullElse(request.chunkStrategy(), "recursive"));
        entity.setChunkSize(Objects.requireNonNullElse(request.chunkSize(), 512));
        entity.setChunkOverlap(Objects.requireNonNullElse(request.chunkOverlap(), 64));
        validateChunkConfig(entity);
        return entity;
    }

    @Override
    protected void updateEntity(KnowledgeBase entity, KnowledgeBaseUpdateDTO request) {
        if (request.name() != null) {
            entity.setName(requireName(request.name()));
        }
        if (request.description() != null) {
            entity.setDescription(request.description());
        }
        if (request.visibility() != null) {
            entity.setVisibility(request.visibility());
        }
        if (request.scopeCode() != null) {
            entity.setScopeCode(request.scopeCode());
        }
        if (request.embeddingModel() != null) {
            entity.setEmbeddingModel(request.embeddingModel());
        }
        if (request.chunkStrategy() != null) {
            entity.setChunkStrategy(request.chunkStrategy());
        }
        if (request.chunkSize() != null) {
            entity.setChunkSize(request.chunkSize());
        }
        if (request.chunkOverlap() != null) {
            entity.setChunkOverlap(request.chunkOverlap());
        }
        validateChunkConfig(entity);
    }

    @Override
    protected String entitlementCode() {
        return "kb_count";
    }

    @Override
    protected List<String> optionSearchFields() {
        return List.of("name");
    }

    /** 后台运维分页；绕过个人与记录范围，但始终保留租户范围。 */
    public PageResult<KnowledgeBaseMaintenanceVO> pageMaintenance(
            KnowledgeBaseMaintenancePageDTO request) {
        var decision = enforce(CrudOperation.QUERY, AccessMode.ADMIN_MAINTENANCE);
        var pageable =
                request.toPageable(
                        Sort.by(Sort.Order.desc("createTime"), Sort.Order.desc("id")),
                        MAINTENANCE_SORT_FIELDS);
        var page =
                knowledgeBaseRepository.findAll(
                        Specification.allOf(
                                decision.scopeSpecification(), buildMaintenanceSpec(request)),
                        pageable);
        return new PageResult<>(
                page.getContent().stream().map(this::toMaintenanceVO).toList(),
                page.getTotalElements());
    }

    /** 后台运维详情；使用与分页相同的 ADMIN_MAINTENANCE 安全模式。 */
    public KnowledgeBaseMaintenanceVO getMaintenance(Long id) {
        return toMaintenanceVO(
                requireEntity(id, CrudOperation.GET, AccessMode.ADMIN_MAINTENANCE));
    }

    private Specification<KnowledgeBase> buildMaintenanceSpec(
            KnowledgeBaseMaintenancePageDTO request) {
        return (root, query, criteriaBuilder) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            if (request.getName() != null && !request.getName().isBlank()) {
                predicates.add(
                        criteriaBuilder.like(
                                criteriaBuilder.lower(root.get("name")),
                                "%"
                                        + request.getName()
                                                .trim()
                                                .toLowerCase(Locale.ROOT)
                                        + "%"));
            }
            if (request.getOrgId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("orgId"), request.getOrgId()));
            }
            if (request.getWorkspaceId() != null) {
                predicates.add(
                        criteriaBuilder.equal(root.get("workspaceId"), request.getWorkspaceId()));
            }
            if (request.getOwnerId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("ownerId"), request.getOwnerId()));
            }
            if (request.getVisibility() != null && !request.getVisibility().isBlank()) {
                predicates.add(
                        criteriaBuilder.equal(
                                root.get("visibility"), request.getVisibility().trim()));
            }
            if (request.getStatus() != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), request.getStatus()));
            }
            return criteriaBuilder.and(
                    predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    private KnowledgeBaseMaintenanceVO toMaintenanceVO(KnowledgeBase entity) {
        return new KnowledgeBaseMaintenanceVO(
                entity.getId(),
                entity.getStableId(),
                entity.getOrgId(),
                entity.getWorkspaceId(),
                entity.getOwnerId(),
                entity.getName(),
                entity.getDescription(),
                entity.getVisibility(),
                entity.getScopeCode(),
                entity.getEmbeddingModel(),
                Objects.requireNonNullElse(entity.getChunkStrategy(), "recursive"),
                Objects.requireNonNullElse(entity.getChunkSize(), 512),
                Objects.requireNonNullElse(entity.getChunkOverlap(), 64),
                Objects.requireNonNullElse(entity.getDocumentCount(), 0L),
                entity.getStatus(),
                entity.getCreateTime(),
                entity.getUpdateTime());
    }

    public KnowledgeBaseStatsVO getStats(Long id) {
        return stats(requireEntity(id));
    }

    public KnowledgeBaseStatsVO getMaintenanceStats(Long id) {
        return stats(requireEntity(id, CrudOperation.GET, AccessMode.ADMIN_MAINTENANCE));
    }

    private KnowledgeBaseStatsVO stats(KnowledgeBase knowledgeBase) {
        var id = knowledgeBase.getId();
        var documentCount = knowledgeDocumentRepository.countByKnowledgeBaseId(id);
        var chunkCount = knowledgeDocumentRepository.sumChunkCountByKnowledgeBaseId(id);
        var totalSize = knowledgeDocumentRepository.sumFileSizeByKnowledgeBaseId(id);
        var vectorCount =
                ((Number)
                                entityManager
                                        .createNativeQuery(
                                                "SELECT COUNT(*) FROM ai_knowledge_embedding WHERE metadata ->> 'knowledge_base_id' = CAST(:id AS TEXT)")
                                        .setParameter("id", knowledgeBase.getStableId())
                                        .getSingleResult())
                        .longValue();
        return new KnowledgeBaseStatsVO(documentCount, chunkCount, vectorCount, totalSize);
    }

    public PageResult<KnowledgeDocumentVO> listDocuments(Long id, Pageable pageable) {
        return listDocuments(id, pageable, AccessMode.DEFAULT);
    }

    public PageResult<KnowledgeDocumentVO> listMaintenanceDocuments(Long id, Pageable pageable) {
        return listDocuments(id, pageable, AccessMode.ADMIN_MAINTENANCE);
    }

    private PageResult<KnowledgeDocumentVO> listDocuments(
            Long id, Pageable pageable, AccessMode accessMode) {
        requireEntity(id, CrudOperation.GET, accessMode);
        var page = knowledgeDocumentRepository.findByKnowledgeBaseId(id, pageable);
        return new PageResult<>(
                page.getContent().stream().map(this::toDocumentVO).toList(),
                page.getTotalElements());
    }

    public KnowledgeSearchResponseVO search(KnowledgeSearchDTO request) {
        var channelWeights =
                switch (request.effectiveMode()) {
                    case "vector" -> new ChannelWeights(1.0, 0.0, 0.0);
                    case "keyword" -> new ChannelWeights(0.0, 1.0, 0.0);
                    case "graph" -> new ChannelWeights(0.0, 0.0, 1.0);
                    case "hybrid" -> ChannelWeights.defaults();
                    default -> throw exception(GlobalErrorCode.BAD_REQUEST);
                };
        var subject =
                new AuthorizationSubject(
                        operatorContext.currentOperatorId().orElse(null),
                        operatorContext.currentOwnerId().orElse(null),
                        OrgContext.getCurrentOrgId(),
                        OrgContext.getCurrentWorkspaceId());
        var query =
                new AuthorizedQuery(
                        subject,
                        request.query(),
                        request.effectiveKnowledgeBaseIds(),
                        request.effectiveIncludePublic(),
                        request.effectiveKnowledgeBaseWeights(),
                        channelWeights,
                        request.effectiveTopK(),
                        request.effectiveThreshold(),
                        request.effectiveSourceFilters());
        return toSearchResponse(hybridSearchService.search(query));
    }

    public KnowledgeGraphVO getGraph(Long id) {
        var knowledgeBase = requireEntity(id);
        var snapshot = graphService.snapshot(knowledgeBase.getStableId());
        return new KnowledgeGraphVO(
                snapshot.nodes().stream()
                        .map(
                                node ->
                                        new KnowledgeGraphVO.GraphNodeVO(
                                                node.id(),
                                                node.name(),
                                                node.type(),
                                                node.description()))
                        .toList(),
                snapshot.edges().stream()
                        .map(
                                edge ->
                                        new KnowledgeGraphVO.GraphEdgeVO(
                                                edge.id(),
                                                edge.factKey(),
                                                edge.sourceId(),
                                                edge.targetId(),
                                                edge.predicate(),
                                                edge.confidence(),
                                                edge.evidenceIds()))
                        .toList());
    }

    public GraphProjectionStatusVO getGraphProjectionStatus(Long id) {
        var knowledgeBase =
                requireEntity(id, CrudOperation.GET, AccessMode.ADMIN_MAINTENANCE);
        return graphProjectionStatus(knowledgeBase);
    }

    public GraphProjectionStatusVO rebuildGraphProjection(Long id, String requestKey) {
        var knowledgeBase =
                requireEntity(id, CrudOperation.UPDATE, AccessMode.ADMIN_MAINTENANCE);
        graphProjectionService.rebuild(knowledgeBase.getStableId(), requestKey);
        return graphProjectionStatus(knowledgeBase);
    }

    private GraphProjectionStatusVO graphProjectionStatus(KnowledgeBase knowledgeBase) {
        var status =
                truthStore
                        .graphProjectionStatus(knowledgeBase.getStableId())
                        .orElseThrow(() -> exception(GlobalErrorCode.NOT_FOUND));
        return new GraphProjectionStatusVO(
                status.knowledgeBaseId(),
                "NEO4J",
                status.baseWatermark(),
                status.desiredWatermark(),
                status.appliedWatermark(),
                status.status(),
                status.rebuildRequestKey(),
                status.errorMessage(),
                status.updatedAt(),
                status.ready());
    }

    public KnowledgeDocumentVO getDocument(Long id, Long documentId) {
        requireEntity(id);
        return toDocumentVO(requireDocument(id, documentId));
    }

    @Transactional
    public void deleteDocument(Long id, Long documentId) {
        requireEntity(id, CrudOperation.UPDATE, AccessMode.DEFAULT);
        try {
            executionLeaseService.execute(
                    documentId,
                    guard -> {
                        var document = requireDocument(id, documentId);
                        guard.run();
                        pipelineService.revokeDocument(documentId);
                        guard.run();
                        knowledgeDocumentRepository.delete(document);
                        enqueueCleanupAfterCommit(document);
                    });
        } catch (TaskExecutionInProgressException inProgress) {
            throw documentProcessingConflict(inProgress);
        }
    }

    @Transactional
    public KnowledgeDocumentVO retryDocument(Long id, Long documentId) {
        return retryDocument(id, documentId, AccessMode.DEFAULT);
    }

    @Transactional
    public KnowledgeDocumentVO retryMaintenanceDocument(Long id, Long documentId) {
        return retryDocument(id, documentId, AccessMode.ADMIN_MAINTENANCE);
    }

    private KnowledgeDocumentVO retryDocument(
            Long id, Long documentId, AccessMode accessMode) {
        requireEntity(id, CrudOperation.UPDATE, accessMode);
        try {
            return executionLeaseService.executeResult(
                    documentId,
                    guard -> {
                        var document = requireDocument(id, documentId);
                        if (!DocumentStatusEnum.FAILED.getCode().equals(document.getStatus())) {
                            throw exception(GlobalErrorCode.BAD_REQUEST);
                        }
                        guard.run();
                        document.setStatus(DocumentStatusEnum.PENDING.getCode());
                        document.setErrorMessage(null);
                        document.setChunkCount(0);
                        var saved = knowledgeDocumentRepository.save(document);
                        guard.run();
                        enqueueAfterCommit(saved);
                        return toDocumentVO(saved);
                    });
        } catch (TaskExecutionInProgressException inProgress) {
            throw documentProcessingConflict(inProgress);
        }
    }

    private BusinessException documentProcessingConflict(
            TaskExecutionInProgressException inProgress) {
        return new BusinessException(409, inProgress.getMessage());
    }

    @Transactional
    public List<KnowledgeDocumentVO> batchImportDocuments(Long id, MultipartFile[] files) {
        var knowledgeBase = requireEntity(id, CrudOperation.UPDATE, AccessMode.DEFAULT);
        return uploadService.upload(knowledgeBase, files).stream().map(this::toDocumentVO).toList();
    }

    public BatchImportProgressVO getImportProgress(Long id) {
        requireEntity(id);
        var total = knowledgeDocumentRepository.countByKnowledgeBaseId(id);
        var completed =
                knowledgeDocumentRepository.countByKnowledgeBaseIdAndStatus(
                        id, DocumentStatusEnum.COMPLETED.getCode());
        var failed =
                knowledgeDocumentRepository.countByKnowledgeBaseIdAndStatus(
                        id, DocumentStatusEnum.FAILED.getCode());
        var finished = completed + failed;
        var status = finished < total ? "PROCESSING" : failed > 0 ? "FAILED" : "COMPLETED";
        return new BatchImportProgressVO(
                Math.toIntExact(total),
                Math.toIntExact(completed),
                Math.toIntExact(failed),
                status);
    }

    private void validateChunkConfig(KnowledgeBase knowledgeBase) {
        var chunkSize = knowledgeBase.getChunkSize();
        var chunkOverlap = knowledgeBase.getChunkOverlap();
        if (chunkSize == null
                || chunkSize <= 0
                || chunkOverlap == null
                || chunkOverlap < 0
                || chunkOverlap >= chunkSize) {
            throw exception(GlobalErrorCode.BAD_REQUEST);
        }
    }

    private String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw exception(GlobalErrorCode.BAD_REQUEST);
        }
        return name.trim();
    }

    private KnowledgeSearchResponseVO toSearchResponse(Response response) {
        var items =
                response.hits().stream()
                        .map(
                                hit ->
                                        new SearchResultItemVO(
                                                hit.candidateKey(),
                                                hit.content(),
                                                hit.score(),
                                                hit.matchedChannels().stream()
                                                        .map(Enum::name)
                                                        .collect(
                                                                java.util.stream.Collectors
                                                                        .toUnmodifiableSet()),
                                                new SourceVO(
                                                        hit.source().knowledgeBaseId(),
                                                        hit.source().knowledgeBaseName(),
                                                        hit.source().visibility().name(),
                                                        hit.source().documentId(),
                                                        hit.source().sourceType(),
                                                        hit.source().sourceKey(),
                                                        hit.source().sourceUri(),
                                                        hit.source().runId(),
                                                        hit.source().focusChunkId(),
                                                        hit.source().factIds(),
                                                        hit.source().evidenceIds())))
                        .toList();
        return new KnowledgeSearchResponseVO(
                items,
                response.searchedKnowledgeBaseIds(),
                response.degradedChannels().stream()
                        .map(Enum::name)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()));
    }

    private KnowledgeDocument requireDocument(Long knowledgeBaseId, Long documentId) {
        return knowledgeDocumentRepository
                .findByIdAndKnowledgeBaseId(documentId, knowledgeBaseId)
                .orElseThrow(() -> exception(GlobalErrorCode.NOT_FOUND));
    }

    private void enqueueAfterCommit(KnowledgeDocument document) {
        requireTransactionSynchronization("知识库文档重试必须在事务中执行");
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        queueService.enqueue(document);
                    }
                });
    }

    private void enqueueCleanupAfterCommit(KnowledgeDocument document) {
        requireTransactionSynchronization("知识库文档删除必须在事务中执行");
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        cleanupQueueService.enqueue(document);
                    }
                });
    }

    private void requireTransactionSynchronization(String message) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException(message);
        }
    }

    private KnowledgeDocumentVO toDocumentVO(KnowledgeDocument entity) {
        return new KnowledgeDocumentVO(
                entity.getId(),
                entity.getStableId(),
                entity.getKnowledgeBaseId(),
                entity.getSourceDocumentId(),
                entity.getUploadedBy(),
                entity.getSourceType(),
                entity.getSourceKey(),
                entity.getSourceUri(),
                entity.getActiveRunId(),
                entity.getTitle(),
                entity.getFileType(),
                entity.getFileSize(),
                entity.getContentHash(),
                entity.getStatus(),
                entity.getErrorMessage(),
                entity.getChunkCount(),
                entity.getCreateTime(),
                entity.getUpdateTime());
    }
}
