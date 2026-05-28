/**
 * Agent 管理接口。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.module.intelligent.agent;

import org.springframework.data.domain.Pageable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.Result;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

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
    public Result<PageResult<AgentVO>> list(@RequestParam(required = false) String status, Pageable pageable) {
        return Result.success(agentService.list(status, pageable));
    }

    @Operation(summary = "Agent 详情")
    @GetMapping("/{id}")
    public Result<AgentVO> getById(@PathVariable Long id) {
        return Result.success(agentService.getById(id));
    }

    @Operation(summary = "更新 Agent")
    @PutMapping("/{id}")
    public Result<AgentVO> update(@PathVariable Long id, @Validated @RequestBody AgentUpdateDTO dto) {
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
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam String status) {
        agentService.updateStatus(id, status);
        return Result.success();
    }
}
