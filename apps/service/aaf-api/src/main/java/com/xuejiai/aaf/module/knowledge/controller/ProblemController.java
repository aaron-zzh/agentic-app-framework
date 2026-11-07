package com.xuejiai.aaf.module.knowledge.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.knowledge.service.ProblemService;

import lombok.RequiredArgsConstructor;

/** QA 问题管理控制器 */
@RestController
@RequestMapping("/api/knowledge/{kbId}/problems")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ProblemController {

    private final ProblemService problemService;

    @GetMapping
    public Result<?> list(@PathVariable Long kbId, Pageable pageable) {
        return Result.success(problemService.list(kbId, pageable));
    }

    @PostMapping
    public Result<?> create(@PathVariable Long kbId, @RequestBody CreateProblemDTO dto) {
        return Result.success(problemService.create(kbId, dto.content(), dto.chunkIds()));
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        problemService.delete(id);
        return Result.success();
    }

    @PostMapping("/{id}/chunks/{chunkId}")
    public Result<?> linkChunk(@PathVariable Long id, @PathVariable UUID chunkId) {
        problemService.linkChunk(id, chunkId);
        return Result.success();
    }

    record CreateProblemDTO(String content, List<UUID> chunkIds) {}
}
