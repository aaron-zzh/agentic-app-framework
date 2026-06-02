package com.xuejiai.aaf.module.document.controller;

import java.util.List;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.document.domain.Document;
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

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
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

    @Operation(summary = "新建文档")
    @PostMapping
    public Result<Document> create(@Valid @RequestBody DocCreateDTO dto) {
        return Result.success(documentService.create(dto));
    }

    @Operation(summary = "更新文档内容")
    @PutMapping("/{id}")
    public Result<Document> update(@PathVariable Long id, @Valid @RequestBody DocUpdateDTO dto) {
        return Result.success(documentService.update(id, dto.content()));
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
}
