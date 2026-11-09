package com.xuejiai.aaf.module.knowledge.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
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
import com.xuejiai.aaf.framework.engine.knowledge.KnowledgeVectorService;
import com.xuejiai.aaf.framework.engine.knowledge.embedding.EmbeddingProperties;
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
import com.xuejiai.aaf.module.document.api.DocumentReferenceApi;
import com.xuejiai.aaf.module.document.api.DocumentReferenceApi.OwnedDocument;
import com.xuejiai.aaf.module.document.api.DocumentSourceApi;
import com.xuejiai.aaf.module.document.api.DocumentVersionApi;
import com.xuejiai.aaf.module.document.api.DocumentVersionApi.DocumentVersionView;
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
import com.xuejiai.aaf.module.knowledge.vo.KnowledgeChunkVO;
import com.xuejiai.aaf.module.knowledge.vo.KnowledgeDocumentLibraryImportVO;
import com.xuejiai.aaf.module.knowledge.vo.KnowledgeDocumentLibraryImportVO.SkippedDocumentVO;
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

    private static final int MAX_LIBRARY_IMPORT_DOCUMENTS = 20;
    private static final int MAX_LIBRARY_FILENAME_BASE_LENGTH = 196;
    private static final String KNOWLEDGE_SOURCE_DOCUMENT_TYPE = "knowledge_source";

    /** 文档库 PDF 导入文档类型，与 DocImportService.PDF_IMPORT_DOC_TYPE 一致。 */
    private static final String PDF_IMPORT_DOCUMENT_TYPE = "pdf_import";

    private static final String LIBRARY_SOURCE_TYPE = "BUSINESS_OBJECT";
    private static final Set<String> PLATFORM_ADMIN_AUTHORITIES =
            Set.of("ROLE_ADMIN", "ROLE_SUPER_ADMIN");

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
    private final KnowledgeVectorService knowledgeVectorService;
    private final KnowledgeDocumentCleanupQueueService cleanupQueueService;
    private final EntityManager entityManager;
    private final OperatorContext operatorContext;
    private final EmbeddingProperties embeddingProperties;
    private final DocumentReferenceApi documentReferenceApi;
    private final DocumentVersionApi documentVersionApi;
    private final DocumentSourceApi documentSourceApi;

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
                entity.getAiSummary(),
                entity.getAiSummaryUpdatedAt(),
                entity.getVisibility(),
                entity.getScopeCode(),
                entity.getEmbeddingModel(),
                Objects.requireNonNullElse(entity.getChunkStrategy(), "auto"),
                Objects.requireNonNullElse(entity.getChunkSize(), 800),
                Objects.requireNonNullElse(entity.getChunkOverlap(), 50),
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
        // 创建时记录当时使用的全局向量模型 modelId，只读；向量模型全局唯一，不支持按库指定或后续修改。
        entity.setEmbeddingModel(embeddingProperties.model());
        entity.setChunkStrategy(Objects.requireNonNullElse(request.chunkStrategy(), "auto"));
        entity.setChunkSize(Objects.requireNonNullElse(request.chunkSize(), 800));
        entity.setChunkOverlap(Objects.requireNonNullElse(request.chunkOverlap(), 50));
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
    protected void beforeDelete(KnowledgeBase entity) {
        // 被助理绑定为默认知识库的库不可删除，否则助理会失去默认知识库；用户可以清空其中的文档
        var bound =
                (Number)
                        entityManager
                                .createNativeQuery(
                                        "SELECT COUNT(*) FROM ai_assistant WHERE knowledge_base_id = :id AND deleted = false")
                                .setParameter("id", entity.getId())
                                .getSingleResult();
        if (bound.longValue() > 0) {
            throw new BusinessException(
                    GlobalErrorCode.BAD_REQUEST, "该知识库是助理的默认知识库，不能删除，可以清空其中的文档");
        }
    }

    /** 平台管理员（ADMIN / SUPER_ADMIN）维护公开库与组织库，不占个人套餐的知识库数量额度；创建不扣减，删除也不归还。 */
    @Override
    protected String entitlementCode() {
        return isPlatformAdmin() ? null : "kb_count";
    }

    private static boolean isPlatformAdmin() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.getAuthorities().stream()
                        .anyMatch(
                                authority ->
                                        PLATFORM_ADMIN_AUTHORITIES.contains(
                                                authority.getAuthority()));
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
        return toMaintenanceVO(requireEntity(id, CrudOperation.GET, AccessMode.ADMIN_MAINTENANCE));
    }

    private Specification<KnowledgeBase> buildMaintenanceSpec(
            KnowledgeBaseMaintenancePageDTO request) {
        return (root, query, criteriaBuilder) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            if (request.getName() != null && !request.getName().isBlank()) {
                predicates.add(
                        criteriaBuilder.like(
                                criteriaBuilder.lower(root.get("name")),
                                "%" + request.getName().trim().toLowerCase(Locale.ROOT) + "%"));
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
                entity.getAiSummary(),
                entity.getAiSummaryUpdatedAt(),
                entity.getVisibility(),
                entity.getScopeCode(),
                entity.getEmbeddingModel(),
                Objects.requireNonNullElse(entity.getChunkStrategy(), "auto"),
                Objects.requireNonNullElse(entity.getChunkSize(), 800),
                Objects.requireNonNullElse(entity.getChunkOverlap(), 50),
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
        var knowledgeBase = requireEntity(id, CrudOperation.GET, AccessMode.ADMIN_MAINTENANCE);
        return graphProjectionStatus(knowledgeBase);
    }

    public GraphProjectionStatusVO rebuildGraphProjection(Long id, String requestKey) {
        return rebuildGraphProjection(id, requestKey, AccessMode.ADMIN_MAINTENANCE);
    }

    /** 知识库所有者重建自己知识库的图投影：按普通写权限校验，不跨 owner。 */
    public GraphProjectionStatusVO rebuildOwnGraphProjection(Long id, String requestKey) {
        return rebuildGraphProjection(id, requestKey, AccessMode.DEFAULT);
    }

    private GraphProjectionStatusVO rebuildGraphProjection(
            Long id, String requestKey, AccessMode accessMode) {
        var knowledgeBase = requireEntity(id, CrudOperation.UPDATE, accessMode);
        graphProjectionService.rebuild(knowledgeBase.getStableId(), requestKey);
        return graphProjectionStatus(knowledgeBase);
    }

    public GraphProjectionStatusVO getVectorProjectionStatus(Long id) {
        var knowledgeBase = requireEntity(id, CrudOperation.GET, AccessMode.ADMIN_MAINTENANCE);
        return projectionStatus(knowledgeBase, "PGVECTOR");
    }

    /** 同步重建向量投影：按当前全局向量模型为活动 chunk 重新生成向量，费用记到入库批次的付费用户。 */
    public GraphProjectionStatusVO rebuildVectorProjection(Long id) {
        var knowledgeBase = requireEntity(id, CrudOperation.UPDATE, AccessMode.ADMIN_MAINTENANCE);
        knowledgeVectorService.rebuild(knowledgeBase.getStableId());
        return projectionStatus(knowledgeBase, "PGVECTOR");
    }

    private GraphProjectionStatusVO graphProjectionStatus(KnowledgeBase knowledgeBase) {
        return projectionStatus(knowledgeBase, "NEO4J");
    }

    private GraphProjectionStatusVO projectionStatus(
            KnowledgeBase knowledgeBase, String projectionKind) {
        var status =
                truthStore
                        .projectionStatus(knowledgeBase.getStableId(), projectionKind)
                        .orElseThrow(() -> exception(GlobalErrorCode.NOT_FOUND));
        return new GraphProjectionStatusVO(
                status.knowledgeBaseId(),
                projectionKind,
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

    /** 文档当前发布代际的分块列表；未发布（处理中或失败）时为空。 */
    public PageResult<KnowledgeChunkVO> listDocumentChunks(
            Long id, Long documentId, Pageable pageable) {
        requireEntity(id);
        var document = requireDocument(id, documentId);
        if (document.getActiveRunId() == null) {
            return new PageResult<>(List.of(), 0L);
        }
        var chunks =
                truthStore
                        .documentChunks(
                                documentId,
                                document.getActiveRunId(),
                                Math.toIntExact(pageable.getOffset()),
                                pageable.getPageSize())
                        .stream()
                        .map(
                                chunk ->
                                        new KnowledgeChunkVO(
                                                chunk.id(),
                                                chunk.chunkIndex(),
                                                chunk.content(),
                                                chunk.tokenCount(),
                                                chunk.sectionPath(),
                                                chunk.pageNo(),
                                                chunk.enabled()))
                        .toList();
        return new PageResult<>(
                chunks, truthStore.countDocumentChunks(documentId, document.getActiveRunId()));
    }

    /** 启用/停用分块：停用后不参与检索，仅由其证据支撑的事实从图谱移除。 */
    public void setChunkEnabled(Long id, Long documentId, UUID chunkId, boolean enabled) {
        requireEntity(id, CrudOperation.UPDATE, AccessMode.DEFAULT);
        requireDocument(id, documentId);
        if (!truthStore.setChunkEnabled(documentId, chunkId, enabled)) {
            throw exception(GlobalErrorCode.NOT_FOUND);
        }
    }

    @Transactional
    public void deleteDocument(Long id, Long documentId) {
        requireEntity(id, CrudOperation.UPDATE, AccessMode.DEFAULT);
        revokeAndDeleteDocument(id, documentId);
    }

    private void revokeAndDeleteDocument(Long knowledgeBaseId, Long documentId) {
        try {
            executionLeaseService.execute(
                    documentId,
                    guard -> {
                        var document = requireDocument(knowledgeBaseId, documentId);
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

    private KnowledgeDocumentVO retryDocument(Long id, Long documentId, AccessMode accessMode) {
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

    /** 上传文件到知识库：md/markdown/txt/pdf 先导入文档库生成正文版本，再按该版本导入知识库；其余类型沿用来源档案上传。 */
    @Transactional
    public List<KnowledgeDocumentVO> batchImportDocuments(Long id, MultipartFile[] files) {
        var knowledgeBase = requireEntity(id, CrudOperation.UPDATE, AccessMode.DEFAULT);
        if (files == null || files.length == 0) {
            throw exception(GlobalErrorCode.BAD_REQUEST);
        }
        var result = new ArrayList<KnowledgeDocumentVO>();
        var otherFiles = new ArrayList<MultipartFile>();
        for (var file : files) {
            if (!documentSourceApi.supportsFileImport(file.getOriginalFilename())) {
                otherFiles.add(file);
                continue;
            }
            var ownerId =
                    operatorContext
                            .currentOwnerId()
                            .orElseThrow(() -> exception(GlobalErrorCode.UNAUTHORIZED));
            var source = documentSourceApi.importFile(file, ownerId);
            result.addAll(importFromDocuments(knowledgeBase, List.of(source.id())).imported());
        }
        if (!otherFiles.isEmpty()) {
            uploadService
                    .upload(knowledgeBase, otherFiles.toArray(MultipartFile[]::new))
                    .forEach(document -> result.add(toDocumentVO(document)));
        }
        return List.copyOf(result);
    }

    /** 从文档库导入：先校验知识库 UPDATE 权限，再按文档当前版本导入。 */
    @Transactional
    public KnowledgeDocumentLibraryImportVO importLibraryDocuments(
            Long id, List<Long> documentIds) {
        var knowledgeBase = requireEntity(id, CrudOperation.UPDATE, AccessMode.DEFAULT);
        return importFromDocuments(knowledgeBase, documentIds);
    }

    /**
     * 将当前用户可访问的文档库文档按当前版本导入知识库。
     *
     * <p>正文另存为 Markdown 并复用资源上传链路另建来源记录，原文档版本不受影响；来源键为 {@code
     * doc:{文档ID}:v{版本ID}}，同版本幂等，同一文档的旧版本导入在该库内先撤销删除。调用方负责知识库写权限校验。
     */
    @Transactional
    public KnowledgeDocumentLibraryImportVO importFromDocuments(
            KnowledgeBase knowledgeBase, List<Long> documentIds) {
        if (documentIds == null
                || documentIds.isEmpty()
                || documentIds.size() > MAX_LIBRARY_IMPORT_DOCUMENTS) {
            throw exception(GlobalErrorCode.BAD_REQUEST);
        }
        var ownerId =
                operatorContext
                        .currentOwnerId()
                        .orElseThrow(() -> exception(GlobalErrorCode.UNAUTHORIZED));
        var documents =
                documentReferenceApi.requireAccessible(
                        documentIds,
                        ownerId,
                        OrgContext.getCurrentOrgId(),
                        OrgContext.getCurrentWorkspaceId());
        var currentVersions =
                documentVersionApi.findCurrent(documents.stream().map(OwnedDocument::id).toList());
        var imported = new ArrayList<KnowledgeDocumentVO>();
        var skipped = new ArrayList<SkippedDocumentVO>();
        for (var document : documents) {
            var version = currentVersions.get(document.id());
            var skipReason = libraryImportSkipReason(knowledgeBase, document, version);
            if (skipReason != null) {
                skipped.add(new SkippedDocumentVO(document.id(), skipReason));
                continue;
            }
            knowledgeDocumentRepository
                    .findByKnowledgeBaseIdAndSourceKeyStartingWithAndDeletedFalse(
                            knowledgeBase.getId(), librarySourceKeyPrefix(document.id()))
                    .forEach(
                            previous ->
                                    revokeAndDeleteDocument(
                                            knowledgeBase.getId(), previous.getId()));
            var plainText = "PLAIN_TEXT".equals(version.format());
            var saved =
                    uploadService.uploadResource(
                            knowledgeBase,
                            version.content().getBytes(StandardCharsets.UTF_8),
                            libraryFilename(version.title(), document.id(), plainText),
                            plainText ? "text/plain" : "text/markdown",
                            document.id(),
                            librarySourceKey(document.id(), version.id()),
                            LIBRARY_SOURCE_TYPE,
                            libraryFileType(document),
                            ownerId);
            imported.add(toDocumentVO(saved));
        }
        return new KnowledgeDocumentLibraryImportVO(imported, skipped);
    }

    private String libraryImportSkipReason(
            KnowledgeBase knowledgeBase, OwnedDocument document, DocumentVersionView version) {
        if (KNOWLEDGE_SOURCE_DOCUMENT_TYPE.equals(document.documentType())) {
            return "知识来源文档不可导入";
        }
        if (version == null || version.content() == null || version.content().isBlank()) {
            return "文档无正文";
        }
        if (knowledgeDocumentRepository
                .findByKnowledgeBaseIdAndSourceKeyAndDeletedFalse(
                        knowledgeBase.getId(), librarySourceKey(document.id(), version.id()))
                .isPresent()) {
            return "该版本已导入";
        }
        return null;
    }

    /** PDF 导入文档的知识文档类型记为 pdf（处理文件虽是提取文本），其余由处理文件扩展名推出（返回 null）。 */
    private static String libraryFileType(OwnedDocument document) {
        return PDF_IMPORT_DOCUMENT_TYPE.equals(document.documentType()) ? "pdf" : null;
    }

    private static String librarySourceKeyPrefix(Long documentId) {
        return "doc:%d:v".formatted(documentId);
    }

    private static String librarySourceKey(Long documentId, Long versionId) {
        return librarySourceKeyPrefix(documentId) + versionId;
    }

    /** 标题去除路径分隔符、控制字符与文件系统保留字符后作为文件名；为空时使用文档 ID。纯文本版本用 .txt，否则 .md。 */
    private static String libraryFilename(String title, Long documentId, boolean plainText) {
        var base =
                title == null
                        ? ""
                        : title.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "")
                                .replaceAll("^\\.+", "")
                                .trim();
        if (base.isEmpty()) {
            base = "document-" + documentId;
        }
        // 来源文档标题上限 200 字符，预留扩展名
        if (base.length() > MAX_LIBRARY_FILENAME_BASE_LENGTH) {
            var end = MAX_LIBRARY_FILENAME_BASE_LENGTH;
            if (Character.isHighSurrogate(base.charAt(end - 1))) {
                end--;
            }
            base = base.substring(0, end);
        }
        return base + (plainText ? ".txt" : ".md");
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
                entity.getActiveSourceDocumentVersionId(),
                entity.getTitle(),
                entity.getSummary(),
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
