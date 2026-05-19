package com.xuejiai.aaf.module.knowledge.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.module.knowledge.domain.KnowledgeBase;
import com.xuejiai.aaf.module.knowledge.domain.KnowledgeDocument;
import com.xuejiai.aaf.module.knowledge.repository.KnowledgeBaseRepository;
import com.xuejiai.aaf.module.knowledge.repository.KnowledgeDocumentRepository;
import com.xuejiai.aaf.module.knowledge.vo.CreateKnowledgeBaseRequest;
import com.xuejiai.aaf.module.knowledge.vo.KnowledgeBaseStatsVO;
import com.xuejiai.aaf.module.knowledge.vo.KnowledgeBaseVO;
import com.xuejiai.aaf.module.knowledge.vo.KnowledgeDocumentVO;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

/** 知识库业务逻辑。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KnowledgeBaseService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final EntityManager entityManager;

    /** 创建知识库 */
    @Transactional
    public KnowledgeBaseVO create(CreateKnowledgeBaseRequest req) {
        var entity = new KnowledgeBase();
        entity.setName(req.name());
        entity.setDescription(req.description());
        entity.setEmbeddingModel(req.embeddingModel());
        entity.setChunkStrategy(req.chunkStrategy());
        entity.setChunkSize(req.chunkSize());
        entity.setChunkOverlap(req.chunkOverlap());
        return toVO(knowledgeBaseRepository.save(entity));
    }

    /** 分页查询知识库列表 */
    public PageResult<KnowledgeBaseVO> list(Pageable pageable) {
        Page<KnowledgeBase> page = knowledgeBaseRepository.findAll(pageable);
        return new PageResult<>(page.getContent().stream().map(this::toVO).toList(), page.getTotalElements());
    }

    /** 查询知识库详情 */
    public KnowledgeBaseVO getById(Long id) {
        return toVO(findById(id));
    }

    /** 更新知识库 */
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

    /** 软删除知识库 */
    @Transactional
    public void delete(Long id) {
        knowledgeBaseRepository.deleteById(id);
    }

    /** 统计信息 */
    public KnowledgeBaseStatsVO getStats(Long id) {
        findById(id);
        long docCount = knowledgeDocumentRepository.countByKnowledgeBaseId(id);
        long chunkCount = ((Number) entityManager
                .createNativeQuery("SELECT COUNT(*) FROM knowledge_chunk WHERE knowledge_base_id = :id")
                .setParameter("id", id)
                .getSingleResult()).longValue();
        long embeddingCount = ((Number) entityManager
                .createNativeQuery("SELECT COUNT(*) FROM knowledge_embedding WHERE knowledge_base_id = :id")
                .setParameter("id", id)
                .getSingleResult()).longValue();
        return new KnowledgeBaseStatsVO(docCount, chunkCount, embeddingCount);
    }

    /** 查询知识库下的文档列表 */
    public PageResult<KnowledgeDocumentVO> listDocuments(Long id, Pageable pageable) {
        findById(id);
        Page<KnowledgeDocument> page = knowledgeDocumentRepository.findByKnowledgeBaseId(id, pageable);
        return new PageResult<>(page.getContent().stream().map(this::toDocVO).toList(), page.getTotalElements());
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
