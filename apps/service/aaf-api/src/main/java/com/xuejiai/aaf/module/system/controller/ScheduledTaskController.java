package com.xuejiai.aaf.module.system.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.system.domain.ScheduledTask;
import com.xuejiai.aaf.module.system.service.ScheduledTaskService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 计划任务管理接口。 */
@Tag(name = "计划任务管理")
@RestController
@RequestMapping("/api/admin/scheduled-tasks")
@RequiredArgsConstructor
public class ScheduledTaskController {

    private final ScheduledTaskService scheduledTaskService;

    @Operation(summary = "查询计划任务列表")
    @GetMapping
    public Result<List<ScheduledTask>> list() {
        return Result.success(scheduledTaskService.list());
    }

    @Operation(summary = "暂停任务")
    @PutMapping("/{id}/pause")
    public Result<Void> pause(@PathVariable Long id) {
        scheduledTaskService.pause(id);
        return Result.success();
    }

    @Operation(summary = "恢复任务")
    @PutMapping("/{id}/resume")
    public Result<Void> resume(@PathVariable Long id) {
        scheduledTaskService.resume(id);
        return Result.success();
    }

    @Operation(summary = "手动触发执行")
    @PostMapping("/{id}/run")
    public Result<Void> run(@PathVariable Long id) {
        scheduledTaskService.runNow(id);
        return Result.success();
    }
}
