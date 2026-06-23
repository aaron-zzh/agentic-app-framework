package com.xuejiai.aaf.module.document.controller;

import java.io.IOException;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.document.domain.Document;
import com.xuejiai.aaf.module.document.repository.DocumentRepository;
import com.xuejiai.aaf.module.document.service.DocumentService;
import com.xuejiai.aaf.module.document.vo.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * 文档管理接口。
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "文档管理")
@RestController
@RequestMapping("/api/docs")
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentRepository documentRepository;
    private final OperatorContext operatorContext;

    public DocumentController(
            DocumentService documentService,
            DocumentRepository documentRepository,
            OperatorContext operatorContext) {
        this.documentService = documentService;
        this.documentRepository = documentRepository;
        this.operatorContext = operatorContext;
    }

    @Operation(summary = "统计用户文档数量")
    @GetMapping("/count")
    public Result<Long> count() {
        Long ownerId = operatorContext.currentUserId().orElse(null);
        if (ownerId == null) return Result.success(0L);
        return Result.success(documentRepository.countByOwnerIdAndStatus(ownerId, "active"));
    }

    @Operation(summary = "获取当前用户文档列表（不含正文）")
    @GetMapping("/list")
    public Result<List<DocListItemVO>> list() {
        Long ownerId = operatorContext.currentUserId().orElse(null);
        if (ownerId == null) return Result.success(List.of());
        return Result.success(documentRepository.listByOwner(ownerId));
    }

    @Operation(summary = "获取文档树")
    @GetMapping("/tree")
    public Result<List<DocTreeNodeVO>> getTree() {
        return Result.success(documentService.getTree());
    }

    @Operation(summary = "新建文档")
    @PostMapping
    public Result<DocTreeNodeVO> create(@Valid @RequestBody DocCreateDTO dto) {
        return Result.success(documentService.create(dto));
    }

    @Operation(summary = "获取文档详情")
    @GetMapping("/{id}")
    public Result<Document> getById(@PathVariable Long id) {
        return Result.success(documentService.getById(id));
    }

    @Operation(summary = "更新文档")
    @PutMapping("/{id}")
    public Result<Document> update(@PathVariable Long id, @RequestBody DocUpdateDTO dto) {
        return Result.success(documentService.update(id, dto));
    }

    @Operation(summary = "发布文档")
    @PostMapping("/{id}/publish")
    public Result<Document> publish(@PathVariable Long id) {
        return Result.success(documentService.publish(id));
    }

    @Operation(summary = "取消发布（转为草稿）")
    @PostMapping("/{id}/unpublish")
    public Result<Document> unpublish(@PathVariable Long id) {
        return Result.success(documentService.unpublish(id));
    }

    @Operation(summary = "获取已发布文档列表（公开端）")
    @GetMapping("/published")
    public Result<List<Document>> getPublished() {
        return Result.success(documentService.getPublished());
    }

    @Operation(summary = "全文检索")
    @GetMapping("/search")
    public Result<List<DocSearchResultVO>> search(@RequestParam String q) {
        return Result.success(documentService.search(q));
    }

    @Operation(summary = "订阅文档变更事件（SSE）")
    @GetMapping("/events")
    public SseEmitter subscribe(@RequestParam(required = false) Long docId) {
        return documentService.subscribe(docId != null ? docId : 0L);
    }

    @Operation(summary = "导入 PDF（上传原文 + 提取文本存入文档库）")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/import-pdf")
    public Result<Document> importPdf(@RequestParam("file") MultipartFile file) throws IOException {
        return Result.success(documentService.importPdf(file));
    }
}
