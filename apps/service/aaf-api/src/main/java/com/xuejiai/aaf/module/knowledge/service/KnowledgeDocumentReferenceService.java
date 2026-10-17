package com.xuejiai.aaf.module.knowledge.service;

import java.util.List;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.module.knowledge.domain.KnowledgeDocument;
import com.xuejiai.aaf.module.knowledge.repository.KnowledgeDocumentRepository;
import com.xuejiai.aaf.module.knowledge.vo.KnowledgeDocumentReferencePageDTO;
import com.xuejiai.aaf.module.knowledge.vo.KnowledgeDocumentVO;

import lombok.RequiredArgsConstructor;

/** 知识文档只读引用服务。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KnowledgeDocumentReferenceService
        extends BaseCrudService<
                KnowledgeDocument,
                KnowledgeDocumentVO,
                Void,
                Void,
                KnowledgeDocumentReferencePageDTO> {

    private final KnowledgeDocumentRepository repository;

    @Override
    protected KnowledgeDocumentRepository getRepository() {
        return repository;
    }

    @Override
    protected KnowledgeDocumentVO toVO(KnowledgeDocument entity) {
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
                entity.getFileType(),
                entity.getFileSize(),
                entity.getContentHash(),
                entity.getStatus(),
                entity.getErrorMessage(),
                entity.getChunkCount(),
                entity.getCreateTime(),
                entity.getUpdateTime());
    }

    @Override
    protected KnowledgeDocument toEntity(Void ignored) {
        throw new BusinessException(GlobalErrorCode.FORBIDDEN, "知识文档引用资源只读");
    }

    @Override
    protected void updateEntity(KnowledgeDocument entity, Void ignored) {
        throw new BusinessException(GlobalErrorCode.FORBIDDEN, "知识文档引用资源只读");
    }

    @Override
    protected Specification<KnowledgeDocument> buildSpec(
            KnowledgeDocumentReferencePageDTO request) {
        return SpecificationBuilder.<KnowledgeDocument>builder()
                .eqIfPresent("knowledgeBaseId", request.getKnowledgeBaseId())
                .likeIfPresent("title", request.getTitle())
                .eqIfPresent("status", request.getStatus())
                .build();
    }

    @Override
    protected List<String> optionSearchFields() {
        return List.of("title");
    }
}
