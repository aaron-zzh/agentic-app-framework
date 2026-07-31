package com.xuejiai.aaf.module.knowledge.controller;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.module.knowledge.domain.KnowledgeBase;
import com.xuejiai.aaf.module.knowledge.service.KnowledgeBaseService;
import com.xuejiai.aaf.module.knowledge.vo.BatchImportProgressVO;
import com.xuejiai.aaf.module.knowledge.vo.CreateKnowledgeBaseRequest;
import com.xuejiai.aaf.module.knowledge.vo.KnowledgeBaseStatsVO;
import com.xuejiai.aaf.module.knowledge.vo.KnowledgeBaseUpdateDTO;
import com.xuejiai.aaf.module.knowledge.vo.KnowledgeBaseVO;
import com.xuejiai.aaf.module.knowledge.vo.KnowledgeDocumentVO;
import com.xuejiai.aaf.module.knowledge.vo.KnowledgeGraphVO;
import com.xuejiai.aaf.module.knowledge.vo.KnowledgeSearchDTO;
import com.xuejiai.aaf.module.knowledge.vo.KnowledgeSearchResponseVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 知识库管理接口；标准 CRUD 统一由 BaseCrud 提供。 */
@Tag(name = "知识库管理")
@RestController
@RequestMapping("/api/knowledge-bases")
@RequiredArgsConstructor
public class KnowledgeBaseController
        extends BaseCrudController<
                KnowledgeBase,
                KnowledgeBaseVO,
                CreateKnowledgeBaseRequest,
                KnowledgeBaseUpdateDTO,
                PageParam> {

    private final KnowledgeBaseService knowledgeBaseService;

    @Override
    protected BaseCrudService<
                    KnowledgeBase,
                    KnowledgeBaseVO,
                    CreateKnowledgeBaseRequest,
                    KnowledgeBaseUpdateDTO,
                    PageParam>
            getService() {
        return knowledgeBaseService;
    }

    @Operation(summary = "知识库统计信息")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}/stats")
    public Result<KnowledgeBaseStatsVO> stats(@PathVariable Long id) {
        return Result.success(knowledgeBaseService.getStats(id));
    }

    @Operation(summary = "检索知识库")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{id}/search")
    public Result<KnowledgeSearchResponseVO> search(
            @PathVariable Long id, @Valid @RequestBody KnowledgeSearchDTO request) {
        return Result.success(knowledgeBaseService.search(id, request));
    }

    @Operation(summary = "查询知识图谱")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}/graph")
    public Result<KnowledgeGraphVO> graph(@PathVariable Long id) {
        return Result.success(knowledgeBaseService.getGraph(id));
    }

    @Operation(summary = "知识库文档列表")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}/documents")
    public Result<PageResult<KnowledgeDocumentVO>> documents(
            @PathVariable Long id, Pageable pageable) {
        return Result.success(knowledgeBaseService.listDocuments(id, pageable));
    }

    @Operation(summary = "知识库文档详情")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}/documents/{documentId}")
    public Result<KnowledgeDocumentVO> document(
            @PathVariable Long id, @PathVariable Long documentId) {
        return Result.success(knowledgeBaseService.getDocument(id, documentId));
    }

    @Operation(summary = "删除知识库文档")
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{id}/documents/{documentId}")
    public Result<Void> deleteDocument(@PathVariable Long id, @PathVariable Long documentId) {
        knowledgeBaseService.deleteDocument(id, documentId);
        return Result.success();
    }

    @Operation(summary = "重试失败知识库文档")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{id}/documents/{documentId}/retry")
    public Result<KnowledgeDocumentVO> retryDocument(
            @PathVariable Long id, @PathVariable Long documentId) {
        return Result.success(knowledgeBaseService.retryDocument(id, documentId));
    }

    @Operation(summary = "批量上传文档")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{id}/documents/batch")
    public Result<List<KnowledgeDocumentVO>> batchImport(
            @PathVariable Long id, @RequestParam("files") MultipartFile[] files) {
        return Result.success(knowledgeBaseService.batchImportDocuments(id, files));
    }

    @Operation(summary = "查询文档处理进度")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}/documents/progress")
    public Result<BatchImportProgressVO> importProgress(@PathVariable Long id) {
        return Result.success(knowledgeBaseService.getImportProgress(id));
    }
}
