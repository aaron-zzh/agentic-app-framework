package com.xuejiai.aaf.module.knowledge.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.data.domain.Pageable;
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
import com.xuejiai.aaf.framework.engine.knowledge.rag.HybridSearchConfig;
import com.xuejiai.aaf.framework.engine.knowledge.rag.HybridSearchService;
import com.xuejiai.aaf.framework.engine.knowledge.rag.RagSearchResult;
import com.xuejiai.aaf.framework.engine.meta.runtime.TaskExecutionInProgressException;
import com.xuejiai.aaf.module.knowledge.domain.KnowledgeBase;
import com.xuejiai.aaf.module.knowledge.domain.KnowledgeDocument;
import com.xuejiai.aaf.module.knowledge.repository.KnowledgeBaseRepository;
import com.xuejiai.aaf.module.knowledge.repository.KnowledgeDocumentRepository;
import com.xuejiai.aaf.module.knowledge.vo.BatchImportProgressVO;
import com.xuejiai.aaf.module.knowledge.vo.CreateKnowledgeBaseRequest;
import com.xuejiai.aaf.module.knowledge.vo.KnowledgeBaseStatsVO;
import com.xuejiai.aaf.module.knowledge.vo.KnowledgeBaseUpdateDTO;
import com.xuejiai.aaf.module.knowledge.vo.KnowledgeBaseVO;
import com.xuejiai.aaf.module.knowledge.vo.KnowledgeDocumentVO;
import com.xuejiai.aaf.module.knowledge.vo.KnowledgeGraphVO;
import com.xuejiai.aaf.module.knowledge.vo.KnowledgeSearchDTO;
import com.xuejiai.aaf.module.knowledge.vo.KnowledgeSearchResponseVO;
import com.xuejiai.aaf.module.knowledge.vo.KnowledgeSearchResponseVO.SearchResultItemVO;

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

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final KnowledgeDocumentUploadService uploadService;
    private final KnowledgeDocumentQueueService queueService;
    private final KnowledgeDocumentExecutionLeaseService executionLeaseService;
    private final HybridSearchService hybridSearchService;
    private final GraphService graphService;
    private final KnowledgePipelineService pipelineService;
    private final KnowledgeDocumentCleanupQueueService cleanupQueueService;
    private final EntityManager entityManager;

    @Override
    protected KnowledgeBaseRepository getRepository() {
        return knowledgeBaseRepository;
    }

    @Override
    protected KnowledgeBaseVO toVO(KnowledgeBase entity) {
        return new KnowledgeBaseVO(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
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

    public KnowledgeBaseStatsVO getStats(Long id) {
        requireEntity(id);
        var documentCount = knowledgeDocumentRepository.countByKnowledgeBaseId(id);
        var chunkCount = knowledgeDocumentRepository.sumChunkCountByKnowledgeBaseId(id);
        var totalSize = knowledgeDocumentRepository.sumFileSizeByKnowledgeBaseId(id);
        var vectorCount =
                ((Number)
                                entityManager
                                        .createNativeQuery(
                                                "SELECT COUNT(*) FROM ai_knowledge_embedding WHERE metadata ->> 'knowledge_base_id' = CAST(:id AS TEXT)")
                                        .setParameter("id", id)
                                        .getSingleResult())
                        .longValue();
        return new KnowledgeBaseStatsVO(documentCount, chunkCount, vectorCount, totalSize);
    }

    public PageResult<KnowledgeDocumentVO> listDocuments(Long id, Pageable pageable) {
        requireEntity(id);
        var page = knowledgeDocumentRepository.findByKnowledgeBaseId(id, pageable);
        return new PageResult<>(
                page.getContent().stream().map(this::toDocumentVO).toList(),
                page.getTotalElements());
    }

    public KnowledgeSearchResponseVO search(Long id, KnowledgeSearchDTO request) {
        requireEntity(id);
        var topK = request.effectiveTopK();
        var threshold = request.effectiveThreshold();
        var results =
                switch (request.effectiveMode()) {
                    case "vector" ->
                            hybridSearchService.vectorSearch(request.query(), id, topK, threshold);
                    case "keyword" ->
                            hybridSearchService.keywordSearch(request.query(), id, topK);
                    case "hybrid" ->
                            hybridSearchService.hybridSearch(
                                    request.query(),
                                    id,
                                    new HybridSearchConfig(0.5, 0.3, 0.2, topK),
                                    threshold);
                    default -> throw exception(GlobalErrorCode.BAD_REQUEST);
                };
        return toSearchResponse(results);
    }

    public KnowledgeGraphVO getGraph(Long id) {
        requireEntity(id);
        var snapshot = graphService.snapshot(id);
        return new KnowledgeGraphVO(
                snapshot.nodes().stream()
                        .map(
                                node ->
                                        new KnowledgeGraphVO.GraphNodeVO(
                                                node.id(),
                                                node.name(),
                                                node.type(),
                                                node.description(),
                                                node.sourceDocumentId()))
                        .toList(),
                snapshot.edges().stream()
                        .map(
                                edge ->
                                        new KnowledgeGraphVO.GraphEdgeVO(
                                                edge.id(),
                                                edge.sourceId(),
                                                edge.targetId(),
                                                edge.type(),
                                                edge.confidence(),
                                                edge.sourceDocumentId()))
                        .toList());
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
                        pipelineService.clearDocumentRelationalData(documentId);
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
        requireEntity(id, CrudOperation.UPDATE, AccessMode.DEFAULT);
        try {
            return executionLeaseService.executeResult(
                    documentId,
                    guard -> {
                        var document = requireDocument(id, documentId);
                        if (!DocumentStatusEnum.FAILED.getCode().equals(document.getStatus())) {
                            throw exception(GlobalErrorCode.BAD_REQUEST);
                        }
                        guard.run();
                        pipelineService.clearDocumentData(id, documentId);
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

    private KnowledgeSearchResponseVO toSearchResponse(List<RagSearchResult> results) {
        var items =
                results.stream()
                        .map(
                                result ->
                                        new SearchResultItemVO(
                                                result.content(),
                                                result.score(),
                                                result.source(),
                                                result.metadata() == null
                                                        ? Map.of()
                                                        : result.metadata()))
                        .toList();
        return new KnowledgeSearchResponseVO(items);
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
                entity.getKnowledgeBaseId(),
                entity.getTitle(),
                entity.getFilePath(),
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
