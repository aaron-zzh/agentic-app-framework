package com.xuejiai.aaf.module.knowledge.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.module.knowledge.domain.KnowledgeDocument;
import com.xuejiai.aaf.module.knowledge.service.KnowledgeDocumentReferenceService;
import com.xuejiai.aaf.module.knowledge.vo.KnowledgeDocumentReferencePageDTO;
import com.xuejiai.aaf.module.knowledge.vo.KnowledgeDocumentVO;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 知识文档只读引用接口。 */
@Tag(name = "知识文档引用")
@RestController
@RequestMapping("/api/knowledge-documents")
@RequiredArgsConstructor
public class KnowledgeDocumentReferenceController
        extends BaseCrudController<
                KnowledgeDocument,
                KnowledgeDocumentVO,
                Void,
                Void,
                KnowledgeDocumentReferencePageDTO> {

    private final KnowledgeDocumentReferenceService service;

    @Override
    protected KnowledgeDocumentReferenceService getService() {
        return service;
    }
}
