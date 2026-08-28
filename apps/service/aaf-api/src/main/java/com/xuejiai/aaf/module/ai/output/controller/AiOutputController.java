package com.xuejiai.aaf.module.ai.output.controller;

import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.output.service.AiOutputService;
import com.xuejiai.aaf.module.ai.output.vo.AiOutputVO;

import lombok.RequiredArgsConstructor;

/** AI 产出接口——查看所有助理工作成果，支持调整和回退。 */
@RestController
@RequestMapping("/api/ai-outputs")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class AiOutputController {

    private final AiOutputService outputService;
    private final OperatorContext operatorContext;

    /** 产出列表（分页+筛选） */
    @GetMapping
    public Result<Page<AiOutputVO>> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) String sourceType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var userId = operatorContext.currentOwnerId().orElseThrow();
        return Result.success(
                outputService.list(userId, category, riskLevel, sourceType, page, size));
    }

    /** 产出详情 */
    @GetMapping("/{id}")
    public Result<AiOutputVO> detail(@PathVariable Long id) {
        return Result.success(outputService.getById(id, currentOwnerId()));
    }

    /** 调整产出 */
    @PostMapping("/{id}/adjust")
    public Result<AiOutputVO> adjust(@PathVariable Long id, @RequestBody AdjustDTO dto) {
        return Result.success(outputService.adjust(id, currentOwnerId(), dto.note()));
    }

    /** 回退产出 */
    @PostMapping("/{id}/revert")
    public Result<AiOutputVO> revert(@PathVariable Long id, @RequestBody RevertDTO dto) {
        return Result.success(outputService.revert(id, currentOwnerId(), dto.reason()));
    }

    /** 统计 */
    @GetMapping("/stats")
    public Result<Map<String, Long>> stats() {
        var userId = operatorContext.currentOwnerId().orElseThrow();
        return Result.success(outputService.stats(userId));
    }

    record AdjustDTO(String note) {}

    record RevertDTO(String reason) {}

    private Long currentOwnerId() {
        return operatorContext.currentOwnerId().orElseThrow();
    }
}
