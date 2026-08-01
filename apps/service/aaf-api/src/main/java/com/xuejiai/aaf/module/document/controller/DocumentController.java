package com.xuejiai.aaf.module.document.controller;

import java.io.IOException;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.document.service.DocumentService;
import com.xuejiai.aaf.module.document.vo.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 文档管理接口。
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "文档管理")
@RestController
@RequestMapping("/api/docs")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @Operation(summary = "统计用户文档数量")
    @GetMapping("/count")
    public Result<Long> count() {
        return Result.success(documentService.countCurrentUser());
    }

    @Operation(summary = "获取当前用户文档列表（不含正文）")
    @GetMapping("/list")
    public Result<List<DocListItemVO>> list() {
        return Result.success(documentService.listCurrentUser());
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
    public Result<DocumentVO> getById(@PathVariable Long id) {
        return Result.success(DocumentVO.from(documentService.getById(id)));
    }

    @Operation(summary = "更新文档")
    @PutMapping("/{id}")
    public Result<DocumentVO> update(@PathVariable Long id, @RequestBody DocUpdateDTO dto) {
        return Result.success(DocumentVO.from(documentService.update(id, dto)));
    }

    @Operation(summary = "发布文档")
    @PostMapping("/{id}/publish")
    public Result<DocumentVO> publish(@PathVariable Long id) {
        return Result.success(DocumentVO.from(documentService.publish(id)));
    }

    @Operation(summary = "取消发布（转为草稿）")
    @PostMapping("/{id}/unpublish")
    public Result<DocumentVO> unpublish(@PathVariable Long id) {
        return Result.success(DocumentVO.from(documentService.unpublish(id)));
    }

    @Operation(summary = "删除文档（逻辑删除）")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        documentService.delete(id);
        return Result.success(null);
    }

    @Operation(summary = "获取已发布文档列表（公开端）")
    @GetMapping("/published")
    public Result<List<DocumentVO>> getPublished() {
        return Result.success(
                documentService.getPublished().stream().map(DocumentVO::from).toList());
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
    public Result<DocumentVO> importPdf(@RequestParam("file") MultipartFile file)
            throws IOException {
        return Result.success(DocumentVO.from(documentService.importPdf(file)));
    }
}
