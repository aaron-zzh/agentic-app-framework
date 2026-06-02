package com.xuejiai.aaf.module.knowledge.controller;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.knowledge.service.KnowledgeSegmentService;
import com.xuejiai.aaf.module.knowledge.vo.KnowledgeSegmentVO;
import com.xuejiai.aaf.module.knowledge.vo.SegmentCreateDTO;
import com.xuejiai.aaf.module.knowledge.vo.SegmentUpdateDTO;
import com.xuejiai.aaf.module.knowledge.vo.SemanticSearchDTO;
import com.xuejiai.aaf.module.knowledge.vo.SemanticSearchResultVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 知识库段落管理接口。
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "知识库段落管理")
@RestController
@RequestMapping("/api/knowledge-bases/{kbId}/segments")
@RequiredArgsConstructor
public class KnowledgeSegmentController {

    private final KnowledgeSegmentService segmentService;

    @Operation(summary = "段落列表（按文档分页）")
    @GetMapping
    public Result<PageResult<KnowledgeSegmentVO>> list(
            @PathVariable Long kbId, @RequestParam Long documentId, Pageable pageable) {
        return Result.success(segmentService.listByDocument(documentId, pageable));
    }

    @Operation(summary = "创建段落")
    @PostMapping
    public Result<KnowledgeSegmentVO> create(
            @PathVariable Long kbId, @Validated @RequestBody SegmentCreateDTO dto) {
        return Result.success(segmentService.create(kbId, dto));
    }

    @Operation(summary = "更新段落")
    @PutMapping("/{id}")
    public Result<KnowledgeSegmentVO> update(
            @PathVariable Long kbId,
            @PathVariable Long id,
            @Validated @RequestBody SegmentUpdateDTO dto) {
        return Result.success(segmentService.update(id, dto));
    }

    @Operation(summary = "删除段落")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long kbId, @PathVariable Long id) {
        segmentService.delete(id);
        return Result.success();
    }

    @Operation(summary = "切换段落启用状态")
    @PatchMapping("/{id}/enabled")
    public Result<Void> toggleEnabled(
            @PathVariable Long kbId, @PathVariable Long id, @RequestParam Boolean enabled) {
        segmentService.toggleEnabled(id, enabled);
        return Result.success();
    }

    @Operation(summary = "语义搜索")
    @PostMapping("/search")
    public Result<List<SemanticSearchResultVO>> semanticSearch(
            @PathVariable Long kbId, @Validated @RequestBody SemanticSearchDTO dto) {
        var topK = dto.topK() != null ? dto.topK() : 5;
        return Result.success(segmentService.semanticSearch(kbId, dto.query(), topK));
    }
}
