package com.xuejiai.aaf.module.ai.agent.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.ai.agent.service.AgentManagementService;
import com.xuejiai.aaf.module.ai.agent.vo.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Agent 管理接口。
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "Agent 管理")
@RestController
@RequestMapping("/api/ai/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentManagementService agentService;

    @Operation(summary = "创建 Agent")
    @PostMapping
    public Result<AgentVO> create(@Validated @RequestBody AgentCreateDTO dto) {
        return Result.success(agentService.create(dto));
    }

    @Operation(summary = "Agent 列表（分页）")
    @GetMapping
    public Result<PageResult<AgentVO>> list(
            @RequestParam(required = false) String status, Pageable pageable) {
        return Result.success(agentService.list(status, pageable));
    }

    @Operation(summary = "Agent 详情")
    @GetMapping("/{id}")
    public Result<AgentVO> getById(@PathVariable Long id) {
        return Result.success(agentService.getById(id));
    }

    @Operation(summary = "更新 Agent")
    @PutMapping("/{id}")
    public Result<AgentVO> update(@PathVariable Long id, @RequestBody AgentUpdateDTO dto) {
        return Result.success(agentService.update(id, dto));
    }

    @Operation(summary = "删除 Agent")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        agentService.delete(id);
        return Result.success();
    }

    @Operation(summary = "启用/禁用 Agent")
    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam String status) {
        agentService.updateStatus(id, status);
        return Result.success();
    }

    @Operation(summary = "启动 Agent 执行")
    @PostMapping("/{id}/execute")
    public Result<String> execute(@PathVariable Long id, @RequestBody String input) {
        return Result.success(agentService.execute(id, input));
    }

    @Operation(summary = "停止 Agent 执行")
    @PostMapping("/executions/{executionId}/stop")
    public Result<Void> stop(@PathVariable String executionId) {
        agentService.stop(executionId);
        return Result.success();
    }

    @Operation(summary = "查询执行状态")
    @GetMapping("/executions/{executionId}")
    public Result<AgentExecutionVO> getExecutionStatus(@PathVariable String executionId) {
        return Result.success(agentService.getExecutionStatus(executionId));
    }

    @Operation(summary = "Agent 执行历史（分页）")
    @GetMapping("/{agentId}/executions")
    public Result<PageResult<AgentExecutionVO>> listExecutions(
            @PathVariable String agentId, Pageable pageable) {
        return Result.success(agentService.listExecutions(agentId, pageable));
    }
}
