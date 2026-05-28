package com.xuejiai.aaf.module.knowledge.controller;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.knowledge.service.KnowledgeBaseService;
import com.xuejiai.aaf.module.knowledge.vo.BatchImportProgressVO;
import com.xuejiai.aaf.module.knowledge.vo.CreateKnowledgeBaseRequest;
import com.xuejiai.aaf.module.knowledge.vo.KnowledgeBaseStatsVO;
import com.xuejiai.aaf.module.knowledge.vo.KnowledgeBaseVO;
import com.xuejiai.aaf.module.knowledge.vo.KnowledgeDocumentVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 知识库管理接口。
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "知识库管理")
@RestController
@RequestMapping("/api/knowledge-bases")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    @Operation(summary = "创建知识库")
    @PostMapping
    public Result<KnowledgeBaseVO> create(@Validated @RequestBody CreateKnowledgeBaseRequest req) {
        return Result.success(knowledgeBaseService.create(req));
    }

    @Operation(summary = "知识库列表（分页）")
    @GetMapping
    public Result<PageResult<KnowledgeBaseVO>> list(Pageable pageable) {
        return Result.success(knowledgeBaseService.list(pageable));
    }

    @Operation(summary = "知识库详情")
    @GetMapping("/{id}")
    public Result<KnowledgeBaseVO> getById(@PathVariable Long id) {
        return Result.success(knowledgeBaseService.getById(id));
    }

    @Operation(summary = "更新知识库")
    @PutMapping("/{id}")
    public Result<KnowledgeBaseVO> update(
            @PathVariable Long id, @Validated @RequestBody CreateKnowledgeBaseRequest req) {
        return Result.success(knowledgeBaseService.update(id, req));
    }

    @Operation(summary = "删除知识库")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        knowledgeBaseService.delete(id);
        return Result.success();
    }

    @Operation(summary = "知识库统计信息")
    @GetMapping("/{id}/stats")
    public Result<KnowledgeBaseStatsVO> stats(@PathVariable Long id) {
        return Result.success(knowledgeBaseService.getStats(id));
    }

    @Operation(summary = "知识库文档列表")
    @GetMapping("/{id}/documents")
    public Result<PageResult<KnowledgeDocumentVO>> documents(
            @PathVariable Long id, Pageable pageable) {
        return Result.success(knowledgeBaseService.listDocuments(id, pageable));
    }

    @Operation(summary = "批量上传文档")
    @PostMapping("/{id}/documents/batch")
    public Result<List<KnowledgeDocumentVO>> batchImport(
            @PathVariable Long id, @RequestParam("files") MultipartFile[] files) {
        return Result.success(knowledgeBaseService.batchImportDocuments(id, files));
    }

    @Operation(summary = "查询文档处理进度")
    @GetMapping("/{id}/documents/progress")
    public Result<BatchImportProgressVO> importProgress(@PathVariable Long id) {
        return Result.success(knowledgeBaseService.getImportProgress(id));
    }
}
