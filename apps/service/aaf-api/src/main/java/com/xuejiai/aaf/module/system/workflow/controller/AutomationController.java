package com.xuejiai.aaf.module.system.workflow.controller;

import java.util.List;

import org.springdoc.core.annotations.ParameterObject;
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

import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.system.workflow.service.AutomationService;
import com.xuejiai.aaf.module.system.workflow.vo.AutomationLogPageDTO;
import com.xuejiai.aaf.module.system.workflow.vo.AutomationLogVO;
import com.xuejiai.aaf.module.system.workflow.vo.AutomationRuleCreateDTO;
import com.xuejiai.aaf.module.system.workflow.vo.AutomationRuleVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 自动化规则接口
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "自动化规则")
@RestController
@RequestMapping("/api/automation")
@RequiredArgsConstructor
public class AutomationController {

    private final AutomationService automationService;

    @Operation(summary = "创建规则")
    @PostMapping("/rules")
    public Result<Long> createRule(@Validated @RequestBody AutomationRuleCreateDTO dto) {
        return Result.success(automationService.createRule(dto));
    }

    @Operation(summary = "更新规则")
    @PutMapping("/rules/{id}")
    public Result<Void> updateRule(
            @PathVariable Long id, @Validated @RequestBody AutomationRuleCreateDTO dto) {
        automationService.updateRule(id, dto);
        return Result.success();
    }

    @Operation(summary = "删除规则")
    @DeleteMapping("/rules/{id}")
    public Result<Void> deleteRule(@PathVariable Long id) {
        automationService.deleteRule(id);
        return Result.success();
    }

    @Operation(summary = "获取规则详情")
    @GetMapping("/rules/{id}")
    public Result<AutomationRuleVO> getRule(@PathVariable Long id) {
        return Result.success(automationService.getRule(id));
    }

    @Operation(summary = "查询规则列表")
    @GetMapping("/rules")
    public Result<List<AutomationRuleVO>> listRules(
            @RequestParam(required = false) String entitySlug) {
        return Result.success(automationService.listRules(entitySlug));
    }

    @Operation(summary = "启用/禁用规则")
    @PutMapping("/rules/{id}/toggle")
    public Result<Void> toggleRule(@PathVariable Long id, @RequestParam boolean enabled) {
        automationService.toggleRule(id, enabled);
        return Result.success();
    }

    @Operation(summary = "分页查询执行日志")
    @GetMapping("/logs")
    public Result<PageResult<AutomationLogVO>> pageLogs(
            @Validated @ParameterObject AutomationLogPageDTO req) {
        return Result.success(automationService.pageLogs(req));
    }
}
