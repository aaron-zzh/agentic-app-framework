package com.xuejiai.aaf.module.ai.agent.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.ai.agent.service.AgentDefinitionService;
import com.xuejiai.aaf.module.ai.agent.vo.AgentDefinitionCreateDTO;
import com.xuejiai.aaf.module.ai.agent.vo.AgentDefinitionUpdateDTO;
import com.xuejiai.aaf.module.ai.agent.vo.AgentDefinitionVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 管理员维护预定义子智能体模板的接口。 */
@Tag(name = "预定义智能体模板管理")
@RestController
@RequestMapping("/api/ai/agent-definitions")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@RequiredArgsConstructor
@Validated
public class AgentDefinitionController {

    private final AgentDefinitionService service;

    @Operation(summary = "创建预定义智能体模板")
    @PostMapping
    public Result<AgentDefinitionVO> create(@Valid @RequestBody AgentDefinitionCreateDTO request) {
        return Result.success(service.create(request));
    }

    @Operation(summary = "分页查询预定义智能体模板")
    @GetMapping
    public Result<PageResult<AgentDefinitionVO>> page(
            @RequestParam(required = false) String status, @Valid PageParam pageParam) {
        return Result.success(service.page(status, pageParam));
    }

    @Operation(summary = "查询预定义智能体模板详情")
    @GetMapping("/{id}")
    public Result<AgentDefinitionVO> get(@PathVariable Long id) {
        return Result.success(service.get(id));
    }

    @Operation(summary = "更新预定义智能体模板配置")
    @PutMapping("/{id}")
    public Result<AgentDefinitionVO> update(
            @PathVariable Long id, @Valid @RequestBody AgentDefinitionUpdateDTO request) {
        return Result.success(service.update(id, request));
    }

    @Operation(summary = "启用预定义智能体模板")
    @PutMapping("/{id}/enable")
    public Result<AgentDefinitionVO> enable(@PathVariable Long id) {
        return Result.success(service.enable(id));
    }

    @Operation(summary = "停用预定义智能体模板")
    @PutMapping("/{id}/disable")
    public Result<AgentDefinitionVO> disable(@PathVariable Long id) {
        return Result.success(service.disable(id));
    }

    @Operation(summary = "归档预定义智能体模板")
    @DeleteMapping("/{id}")
    public Result<Void> archive(@PathVariable Long id) {
        service.archive(id);
        return Result.success();
    }
}
