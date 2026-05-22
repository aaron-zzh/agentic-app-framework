package com.xuejiai.aaf.module.document.controller;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.document.domain.Document;
import com.xuejiai.aaf.module.document.service.DocImportService;
import com.xuejiai.aaf.module.document.service.DocumentService;
import com.xuejiai.aaf.module.document.vo.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** 文档管理接口。 */
@Tag(name = "文档管理")
@RestController
@RequestMapping("/api/docs")
public class DocumentController {

    private final DocumentService documentService;
    private final DocImportService docImportService;

    public DocumentController(DocumentService documentService, DocImportService docImportService) {
        this.documentService = documentService;
        this.docImportService = docImportService;
    }

    @Operation(summary = "获取文档树")
    @GetMapping("/tree")
    public Result<List<DocTreeNodeVO>> getTree() {
        return Result.success(documentService.getTree());
    }

    @Operation(summary = "获取文档详情")
    @GetMapping("/{id}")
    public Result<Document> getById(@PathVariable Long id) {
        return Result.success(documentService.getById(id));
    }

    @Operation(summary = "更新文档内容（同步写回本地文件）")
    @PutMapping("/{id}")
    public Result<Document> update(@PathVariable Long id, @Valid @RequestBody DocUpdateDTO dto) {
        return Result.success(documentService.update(id, dto.content()));
    }

    @Operation(summary = "触发全量文档导入")
    @PostMapping("/import")
    public Result<Integer> importDocs() {
        int count = docImportService.importAll();
        return Result.success(count);
    }

    @Operation(summary = "获取文档关系图")
    @GetMapping("/{id}/relations")
    public Result<DocRelationGraphVO> getRelations(@PathVariable Long id) {
        return Result.success(documentService.getRelations(id));
    }

    @Operation(summary = "全文检索")
    @GetMapping("/search")
    public Result<List<DocSearchResultVO>> search(@RequestParam String q) {
        return Result.success(documentService.search(q));
    }
}
