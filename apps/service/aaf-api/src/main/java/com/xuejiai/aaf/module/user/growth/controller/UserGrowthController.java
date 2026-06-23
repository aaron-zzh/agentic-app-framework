package com.xuejiai.aaf.module.user.growth.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.user.growth.service.UserGrowthService;
import com.xuejiai.aaf.module.user.growth.vo.UserGrowthTaskVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 成长任务接口（v0.2.1 P3）。 */
@Tag(name = "成长任务")
@RestController
@RequestMapping("/api/user/growth/tasks")
@RequiredArgsConstructor
public class UserGrowthController {

    private final UserGrowthService growthService;

    @Operation(summary = "我的成长任务（含进度）")
    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public Result<List<UserGrowthTaskVO>> listMyTasks() {
        return Result.success(growthService.listMyTasks());
    }

    @Operation(summary = "领取奖励")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{taskId}/claim")
    public Result<Void> claim(@PathVariable Long taskId) {
        growthService.claim(taskId);
        return Result.success();
    }
}
