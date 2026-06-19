package com.xuejiai.aaf.module.knowledge.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.framework.engine.entitlement.EntitlementChecker;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.knowledge.domain.KnowledgeBase;
import com.xuejiai.aaf.module.knowledge.domain.KnowledgeDocument;
import com.xuejiai.aaf.module.knowledge.repository.KnowledgeBaseRepository;
import com.xuejiai.aaf.module.knowledge.repository.KnowledgeDocumentRepository;
import com.xuejiai.aaf.module.knowledge.vo.BatchImportProgressVO;
import com.xuejiai.aaf.module.knowledge.vo.CreateKnowledgeBaseRequest;
import com.xuejiai.aaf.module.knowledge.vo.KnowledgeBaseStatsVO;
import com.xuejiai.aaf.module.knowledge.vo.KnowledgeBaseVO;
import com.xuejiai.aaf.module.knowledge.vo.KnowledgeDocumentVO;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

/**
 * 知识库管理 Service。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KnowledgeBaseService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final EntityManager entityManager;
    private final EntitlementChecker entitlementChecker;
    private final OperatorContext operatorContext;

    /**
     * 创建知识库
     *
     * @param req 创建请求
     * @return 知识库信息
     */
    @Transactional
    public KnowledgeBaseVO create(CreateKnowledgeBaseRequest req) {
        operatorContext
                .currentOwnerId()
                .ifPresent(uid -> entitlementChecker.checkAndConsume(uid, "kb_count", 1));
        var entity = new KnowledgeBase();
        entity.setName(req.name());
        entity.setDescription(req.description());
        entity.setEmbeddingModel(req.embeddingModel());
        entity.setChunkStrategy(req.chunkStrategy());
        entity.setChunkSize(req.chunkSize());
        entity.setChunkOverlap(req.chunkOverlap());
        return toVO(knowledgeBaseRepository.save(entity));
    }

    /**
     * 分页查询知识库列表
     *
     * @param pageable 分页参数
     * @return 知识库分页结果
     */
    public PageResult<KnowledgeBaseVO> list(Pageable pageable) {
        Page<KnowledgeBase> page = knowledgeBaseRepository.findAll(pageable);
        return new PageResult<>(
                page.getContent().stream().map(this::toVO).toList(), page.getTotalElements());
    }

    /**
     * 查询知识库详情
     *
     * @param id 知识库编号
     * @return 知识库信息
     */
    public KnowledgeBaseVO getById(Long id) {
        return toVO(findById(id));
    }

    /**
     * 更新知识库
     *
     * @param id 知识库编号
     * @param req 更新请求
     * @return 更新后的知识库信息
     */
    @Transactional
    public KnowledgeBaseVO update(Long id, CreateKnowledgeBaseRequest req) {
        var entity = findById(id);
        entity.setName(req.name());
        entity.setDescription(req.description());
        entity.setEmbeddingModel(req.embeddingModel());
        entity.setChunkStrategy(req.chunkStrategy());
        entity.setChunkSize(req.chunkSize());
        entity.setChunkOverlap(req.chunkOverlap());
        return toVO(knowledgeBaseRepository.save(entity));
    }

    /**
     * 删除知识库（软删除）
     *
     * @param id 知识库编号
     */
    @Transactional
    public void delete(Long id) {
        knowledgeBaseRepository.deleteById(id);
        operatorContext
                .currentOwnerId()
                .ifPresent(uid -> entitlementChecker.consume(uid, "kb_count", -1));
    }

    /**
     * 获取知识库统计信息
     *
     * @param id 知识库编号
     * @return 统计信息（文档数/分块数/向量数）
     */
    public KnowledgeBaseStatsVO getStats(Long id) {
        findById(id);
        long docCount = knowledgeDocumentRepository.countByKnowledgeBaseId(id);
        long chunkCount =
                ((Number)
                                entityManager
                                        .createNativeQuery(
                                                "SELECT COUNT(*) FROM ai_knowledge_chunk WHERE knowledge_base_id = :id")
                                        .setParameter("id", id)
                                        .getSingleResult())
                        .longValue();
        long embeddingCount =
                ((Number)
                                entityManager
                                        .createNativeQuery(
                                                "SELECT COUNT(*) FROM ai_knowledge_embedding WHERE knowledge_base_id = :id")
                                        .setParameter("id", id)
                                        .getSingleResult())
                        .longValue();
        return new KnowledgeBaseStatsVO(docCount, chunkCount, embeddingCount);
    }

    /**
     * 分页查询知识库下的文档列表
     *
     * @param id 知识库编号
     * @param pageable 分页参数
     * @return 文档分页结果
     */
    public PageResult<KnowledgeDocumentVO> listDocuments(Long id, Pageable pageable) {
        findById(id);
        Page<KnowledgeDocument> page =
                knowledgeDocumentRepository.findByKnowledgeBaseId(id, pageable);
        return new PageResult<>(
                page.getContent().stream().map(this::toDocVO).toList(), page.getTotalElements());
    }

    /**
     * 批量上传文档
     *
     * @param id 知识库编号
     * @param files 文件数组
     * @return 上传的文档列表
     */
    @Transactional
    public List<KnowledgeDocumentVO> batchImportDocuments(Long id, MultipartFile[] files) {
        findById(id);
        // TODO 委托 framework 层 KnowledgePipelineService 处理文档解析、分块、向量化
        var docs = new java.util.ArrayList<KnowledgeDocumentVO>();
        for (var file : files) {
            var doc = new KnowledgeDocument();
            doc.setKnowledgeBaseId(id);
            doc.setTitle(file.getOriginalFilename());
            doc.setFileType(extractFileType(file.getOriginalFilename()));
            doc.setFileSize(file.getSize());
            knowledgeDocumentRepository.save(doc);
            docs.add(toDocVO(doc));
        }
        return docs;
    }

    /**
     * 查询文档处理进度
     *
     * @param id 知识库编号
     * @return 处理进度信息
     */
    public BatchImportProgressVO getImportProgress(Long id) {
        findById(id);
        // TODO 委托 framework 层 KnowledgePipelineService 查询实际处理进度
        long total = knowledgeDocumentRepository.countByKnowledgeBaseId(id);
        return new BatchImportProgressVO((int) total, (int) total, 0, "COMPLETED");
    }

    private String extractFileType(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "unknown";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    private KnowledgeBase findById(Long id) {
        return knowledgeBaseRepository
                .findById(id)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "知识库不存在"));
    }

    private KnowledgeBaseVO toVO(KnowledgeBase e) {
        return new KnowledgeBaseVO(
                e.getId(),
                e.getName(),
                e.getDescription(),
                e.getEmbeddingModel(),
                e.getChunkStrategy(),
                e.getChunkSize(),
                e.getChunkOverlap(),
                e.getStatus(),
                e.getCreateTime(),
                e.getUpdateTime());
    }

    private KnowledgeDocumentVO toDocVO(KnowledgeDocument e) {
        return new KnowledgeDocumentVO(
                e.getId(),
                e.getKnowledgeBaseId(),
                e.getTitle(),
                e.getFilePath(),
                e.getFileType(),
                e.getFileSize(),
                e.getContentHash(),
                e.getStatus(),
                e.getErrorMessage(),
                e.getChunkCount(),
                e.getCreateTime(),
                e.getUpdateTime());
    }
}
