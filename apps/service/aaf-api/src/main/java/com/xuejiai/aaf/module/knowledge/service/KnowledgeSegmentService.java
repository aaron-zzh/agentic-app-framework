package com.xuejiai.aaf.module.knowledge.service;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.module.knowledge.domain.KnowledgeSegment;
import com.xuejiai.aaf.module.knowledge.repository.KnowledgeSegmentRepository;
import com.xuejiai.aaf.module.knowledge.vo.KnowledgeSegmentVO;
import com.xuejiai.aaf.module.knowledge.vo.SegmentCreateDTO;
import com.xuejiai.aaf.module.knowledge.vo.SegmentUpdateDTO;
import com.xuejiai.aaf.module.knowledge.vo.SemanticSearchResultVO;

import lombok.RequiredArgsConstructor;

/**
 * 知识库段落管理 Service。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KnowledgeSegmentService {

    private final KnowledgeSegmentRepository segmentRepository;

    /**
     * 分页查询文档下的段落列表
     *
     * @param documentId 文档编号
     * @param pageable 分页参数
     * @return 段落分页结果
     */
    public PageResult<KnowledgeSegmentVO> listByDocument(Long documentId, Pageable pageable) {
        var page = segmentRepository.findByDocumentId(documentId, pageable);
        return new PageResult<>(
                page.getContent().stream().map(this::toVO).toList(), page.getTotalElements());
    }

    /**
     * 创建段落
     *
     * @param knowledgeBaseId 知识库编号
     * @param dto 创建请求
     * @return 段落信息
     */
    @Transactional
    public KnowledgeSegmentVO create(Long knowledgeBaseId, SegmentCreateDTO dto) {
        var segment = new KnowledgeSegment();
        segment.setKnowledgeBaseId(knowledgeBaseId);
        segment.setDocumentId(dto.documentId());
        segment.setContent(dto.content());
        segment.setPosition(dto.position() != null ? dto.position() : 0);
        segment.setWordCount(dto.content().length());
        return toVO(segmentRepository.save(segment));
    }

    /**
     * 更新段落内容
     *
     * @param id 段落编号
     * @param dto 更新请求
     * @return 更新后的段落信息
     */
    @Transactional
    public KnowledgeSegmentVO update(Long id, SegmentUpdateDTO dto) {
        var segment = findById(id);
        segment.setContent(dto.content());
        segment.setWordCount(dto.content().length());
        return toVO(segmentRepository.save(segment));
    }

    /**
     * 删除段落（软删除）
     *
     * @param id 段落编号
     */
    @Transactional
    public void delete(Long id) {
        segmentRepository.deleteById(id);
    }

    /**
     * 切换段落启用状态
     *
     * @param id 段落编号
     * @param enabled 是否启用
     */
    @Transactional
    public void toggleEnabled(Long id, Boolean enabled) {
        var segment = findById(id);
        segment.setEnabled(enabled);
        segmentRepository.save(segment);
    }

    /**
     * 语义搜索（委托 framework 层 HybridSearchService）
     *
     * @param knowledgeBaseId 知识库编号
     * @param query 查询文本
     * @param topK 返回结果数量
     * @return 搜索结果列表
     */
    public List<SemanticSearchResultVO> semanticSearch(
            Long knowledgeBaseId, String query, Integer topK) {
        // TODO 委托 framework 层 HybridSearchService 实现语义搜索
        return List.of();
    }

    private KnowledgeSegment findById(Long id) {
        return segmentRepository
                .findById(id)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "段落不存在"));
    }

    private KnowledgeSegmentVO toVO(KnowledgeSegment e) {
        return new KnowledgeSegmentVO(
                e.getId(),
                e.getDocumentId(),
                e.getKnowledgeBaseId(),
                e.getContent(),
                e.getPosition(),
                e.getWordCount(),
                e.getEnabled(),
                e.getCreateTime(),
                e.getUpdateTime());
    }
}
