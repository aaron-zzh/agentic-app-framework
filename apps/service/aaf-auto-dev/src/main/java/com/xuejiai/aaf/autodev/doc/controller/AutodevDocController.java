package com.xuejiai.aaf.autodev.doc.controller;

import java.util.List;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.xuejiai.aaf.autodev.doc.domain.AutodevDoc;
import com.xuejiai.aaf.autodev.doc.service.AutodevDocImportService;
import com.xuejiai.aaf.autodev.doc.service.AutodevDocService;
import com.xuejiai.aaf.autodev.doc.vo.*;
import com.xuejiai.aaf.common.model.Result;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** 开发文档管理接口（docs/ 目录同步，协作开发专用）。 */
@Tag(name = "开发文档管理")
@RestController
@RequestMapping("/api/autodev/docs")
public class AutodevDocController {

    private final AutodevDocService docService;
    private final AutodevDocImportService importService;

    public AutodevDocController(
            AutodevDocService docService, AutodevDocImportService importService) {
        this.docService = docService;
        this.importService = importService;
    }

    @Operation(summary = "获取文档树")
    @GetMapping("/tree")
    public Result<List<AutodevDocTreeNodeVO>> getTree() {
        return Result.success(docService.getTree());
    }

    @Operation(summary = "获取文档详情")
    @GetMapping("/{id}")
    public Result<AutodevDoc> getById(@PathVariable Long id) {
        return Result.success(docService.getById(id));
    }

    @Operation(summary = "新建文档（写入本地文件 + 数据库）")
    @PostMapping
    public Result<AutodevDoc> create(@Valid @RequestBody AutodevDocCreateDTO dto) {
        return Result.success(docService.create(dto));
    }

    @Operation(summary = "更新文档内容（同步写回本地文件）")
    @PutMapping("/{id}")
    public Result<AutodevDoc> update(@PathVariable Long id, @RequestBody String content) {
        return Result.success(docService.update(id, content));
    }

    @Operation(summary = "触发全量文档导入")
    @PostMapping("/import")
    public Result<Integer> importDocs() {
        return Result.success(importService.importAll());
    }

    @Operation(summary = "获取文档关系图")
    @GetMapping("/{id}/relations")
    public Result<AutodevDocRelationGraphVO> getRelations(@PathVariable Long id) {
        return Result.success(docService.getRelations(id));
    }

    @Operation(summary = "全文检索")
    @GetMapping("/search")
    public Result<List<AutodevDocSearchResultVO>> search(@RequestParam String q) {
        return Result.success(docService.search(q));
    }

    @Operation(summary = "订阅文档变更 SSE 事件")
    @GetMapping("/events")
    public SseEmitter subscribe(@RequestParam(required = false) Long docId) {
        return docService.subscribe(docId != null ? docId : 0L);
    }
}
