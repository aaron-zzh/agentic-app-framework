package com.xuejiai.aaf.module.ai.assistant.controller;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.ai.assistant.service.AssistantManagementService;
import com.xuejiai.aaf.module.ai.assistant.vo.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Assistant 管理接口。
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "Assistant 管理")
@RestController
@RequestMapping("/api/ai/assistants")
@RequiredArgsConstructor
public class AssistantController {

    private final AssistantManagementService assistantService;

    @Operation(summary = "创建 Assistant")
    @PostMapping
    public Result<AssistantVO> create(@Validated @RequestBody AssistantCreateDTO dto) {
        return Result.success(assistantService.create(dto));
    }

    @Operation(summary = "Assistant 列表（分页）")
    @GetMapping
    public Result<PageResult<AssistantVO>> list(
            @RequestParam(required = false) Long userId, Pageable pageable) {
        return Result.success(assistantService.list(userId, pageable));
    }

    @Operation(summary = "Assistant 详情")
    @GetMapping("/{id}")
    public Result<AssistantVO> getById(@PathVariable Long id) {
        return Result.success(assistantService.getById(id));
    }

    @Operation(summary = "更新 Assistant")
    @PutMapping("/{id}")
    public Result<AssistantVO> update(@PathVariable Long id, @RequestBody AssistantUpdateDTO dto) {
        return Result.success(assistantService.update(id, dto));
    }

    @Operation(summary = "删除 Assistant")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        assistantService.delete(id);
        return Result.success();
    }

    @Operation(summary = "当前用户可用的助理列表（含头像名称，供前端角色切换）")
    @GetMapping("/available")
    public Result<List<AssistantAvailableVO>> listAvailable() {
        return Result.success(assistantService.listAvailable());
    }

    @Operation(summary = "绑定技能")
    @PutMapping("/{id}/skills")
    public Result<Void> bindSkills(@PathVariable Long id, @RequestBody List<String> skillIds) {
        assistantService.bindSkills(id, skillIds);
        return Result.success();
    }

    @Operation(summary = "配置工具白名单")
    @PutMapping("/{id}/tool-whitelist")
    public Result<Void> configureToolWhitelist(
            @PathVariable Long id, @RequestBody List<String> toolWhitelist) {
        assistantService.configureToolWhitelist(id, toolWhitelist);
        return Result.success();
    }
}
