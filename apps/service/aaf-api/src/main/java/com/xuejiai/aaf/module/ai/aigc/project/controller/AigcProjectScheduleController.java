package com.xuejiai.aaf.module.ai.aigc.project.controller;

import java.time.Instant;
import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.result.Result;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectSchedule;
import com.xuejiai.aaf.module.ai.aigc.project.service.AigcProjectScheduleService;
import com.xuejiai.aaf.module.ai.aigc.project.service.AigcProjectScheduleService.CreateScheduleCommand;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * AIGC 项目定时任务 API。
 *
 * @author Kiro
 */
@RestController
@RequestMapping("/api/aigc/projects/{projectId}/schedules")
@RequiredArgsConstructor
@Tag(name = "AIGC 项目定时任务")
public class AigcProjectScheduleController {

    private final AigcProjectScheduleService scheduleService;
    private final OperatorContext operatorContext;

    @PostMapping
    @Operation(summary = "创建定时任务")
    public Result<Long> createSchedule(
            @PathVariable Long projectId, @RequestBody CreateScheduleRequest request) {
        var command =
                new CreateScheduleCommand(
                        OrgContext.getCurrentOrgId(),
                        OrgContext.getCurrentWorkspaceId(),
                        projectId,
                        operatorContext.currentOperatorId().orElseThrow(),
                        request.executeAt(),
                        request.timezone(),
                        request.assistantId(),
                        request.conversationId(),
                        request.prompt());

        var scheduleId = scheduleService.createSchedule(command);
        return Result.success(scheduleId);
    }

    @GetMapping
    @Operation(summary = "查询项目的所有定时任务")
    public Result<List<ScheduleDTO>> listSchedules(@PathVariable Long projectId) {
        var schedules = scheduleService.listByProject(projectId);
        return Result.success(schedules.stream().map(this::toDTO).toList());
    }

    @DeleteMapping("/{scheduleId}")
    @Operation(summary = "取消定时任务")
    public Result<Void> cancelSchedule(@PathVariable Long projectId, @PathVariable Long scheduleId) {
        scheduleService.cancelSchedule(scheduleId);
        return Result.success();
    }

    @PutMapping("/{scheduleId}/pause")
    @Operation(summary = "暂停定时任务")
    public Result<Void> pauseSchedule(@PathVariable Long projectId, @PathVariable Long scheduleId) {
        scheduleService.pauseSchedule(scheduleId);
        return Result.success();
    }

    @PutMapping("/{scheduleId}/resume")
    @Operation(summary = "恢复定时任务")
    public Result<Void> resumeSchedule(@PathVariable Long projectId, @PathVariable Long scheduleId) {
        scheduleService.resumeSchedule(scheduleId);
        return Result.success();
    }

    private ScheduleDTO toDTO(AigcProjectSchedule schedule) {
        return new ScheduleDTO(
                schedule.getId(),
                schedule.getExecuteAt(),
                schedule.getTimezone(),
                schedule.getPrompt(),
                schedule.getStatus().name(),
                schedule.getLinkedTaskId(),
                schedule.getLastError(),
                schedule.getFailCount(),
                schedule.getCreatedAt());
    }

    /** 创建定时任务请求 */
    public record CreateScheduleRequest(
            Instant executeAt,
            String timezone,
            String assistantId,
            String conversationId,
            String prompt) {}

    /** 定时任务 DTO */
    public record ScheduleDTO(
            Long id,
            Instant executeAt,
            String timezone,
            String prompt,
            String status,
            String linkedTaskId,
            String lastError,
            Integer failCount,
            Instant createdAt) {}
}
