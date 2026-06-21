package com.xuejiai.aaf.module.ai.aigc.image.controller;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.ai.aigc.image.service.BatchGenerationService;
import com.xuejiai.aaf.module.ai.aigc.image.vo.BatchGenerationSubmitDTO;
import com.xuejiai.aaf.module.ai.aigc.image.vo.BatchGenerationTaskVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.xuejiai.aaf.framework.security.license.FeatureRequired;
import com.xuejiai.aaf.framework.security.license.LicenseFeature;

/**
 * AIGC 批量生成接口。
 *
 * @author AaronZZH & Kiro
 */
@FeatureRequired(LicenseFeature.Codes.AIGC)
@Tag(name = "AIGC 批量生成")
@RestController
@RequestMapping("/api/aigc/batch")
@RequiredArgsConstructor
public class BatchGenerationController {

    private final BatchGenerationService batchGenerationService;

    @Operation(summary = "提交批量生成任务")
    @PostMapping
    public Result<BatchGenerationTaskVO> submit(
            @RequestParam Long userId, @Valid @RequestBody BatchGenerationSubmitDTO dto) {
        return Result.success(batchGenerationService.submit(userId, dto));
    }

    @Operation(summary = "查询任务进度")
    @GetMapping("/{taskId}")
    public Result<BatchGenerationTaskVO> getProgress(@PathVariable Long taskId) {
        return Result.success(batchGenerationService.getProgress(taskId));
    }

    @Operation(summary = "查询用户所有批量任务")
    @GetMapping
    public Result<List<BatchGenerationTaskVO>> listByUser(@RequestParam Long userId) {
        return Result.success(batchGenerationService.listByUser(userId));
    }

    @Operation(summary = "取消任务")
    @PostMapping("/{taskId}/cancel")
    public Result<Void> cancel(@PathVariable Long taskId) {
        batchGenerationService.cancel(taskId);
        return Result.success();
    }
}
