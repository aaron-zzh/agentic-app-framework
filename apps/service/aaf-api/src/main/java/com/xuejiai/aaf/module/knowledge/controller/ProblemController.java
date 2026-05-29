package com.xuejiai.aaf.module.knowledge.controller;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.knowledge.service.ProblemService;

import lombok.RequiredArgsConstructor;

/** QA 问题管理控制器 */
@RestController
@RequestMapping("/api/knowledge/{kbId}/problems")
@RequiredArgsConstructor
public class ProblemController {

    private final ProblemService problemService;

    @GetMapping
    public Result<?> list(@PathVariable Long kbId, Pageable pageable) {
        return Result.success(problemService.list(kbId, pageable));
    }

    @PostMapping
    public Result<?> create(@PathVariable Long kbId, @RequestBody CreateProblemDTO dto) {
        return Result.success(problemService.create(kbId, dto.content(), dto.segmentIds()));
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        problemService.delete(id);
        return Result.success();
    }

    @PostMapping("/{id}/segments/{segmentId}")
    public Result<?> linkSegment(@PathVariable Long id, @PathVariable Long segmentId) {
        problemService.linkSegment(id, segmentId);
        return Result.success();
    }

    record CreateProblemDTO(String content, List<Long> segmentIds) {}
}
