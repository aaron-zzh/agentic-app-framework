package com.xuejiai.aaf.module.system.workflow.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.system.workflow.domain.ArchiveRule;
import com.xuejiai.aaf.module.system.workflow.service.ArchiveService;
import com.xuejiai.aaf.module.system.workflow.vo.ArchiveRuleCreateDTO;
import com.xuejiai.aaf.module.system.workflow.vo.ArchiveRuleVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 数据归档接口。 */
@Tag(name = "数据归档")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ArchiveController {

    private final ArchiveService archiveService;

    // ==================== 手动归档/恢复 ====================

    @Operation(summary = "手动归档记录")
    @PostMapping("/{entity}/{id}/archive")
    public Result<Void> archive(@PathVariable String entity, @PathVariable Long id) {
        archiveService.archive(entity, id);
        return Result.success();
    }

    @Operation(summary = "恢复归档记录")
    @PostMapping("/{entity}/{id}/unarchive")
    public Result<Void> unarchive(@PathVariable String entity, @PathVariable Long id) {
        archiveService.unarchive(entity, id);
        return Result.success();
    }

    // ==================== 归档规则 CRUD ====================

    @Operation(summary = "获取归档规则列表")
    @GetMapping("/admin/archive-rules")
    public Result<List<ArchiveRuleVO>> listRules() {
        return Result.success(archiveService.listRules().stream().map(this::toVO).toList());
    }

    @Operation(summary = "获取归档规则详情")
    @GetMapping("/admin/archive-rules/{id}")
    public Result<ArchiveRuleVO> getRule(@PathVariable Long id) {
        return Result.success(toVO(archiveService.getRuleById(id)));
    }

    @Operation(summary = "创建归档规则")
    @PostMapping("/admin/archive-rules")
    @ResponseStatus(HttpStatus.CREATED)
    public Result<ArchiveRuleVO> createRule(@Validated @RequestBody ArchiveRuleCreateDTO dto) {
        var rule = new ArchiveRule();
        rule.setEntitySlug(dto.entitySlug());
        rule.setCondition(dto.condition());
        rule.setAfterDays(dto.afterDays() != null ? dto.afterDays() : 90);
        rule.setEnabled(dto.enabled() != null ? dto.enabled() : true);
        return Result.success(toVO(archiveService.createRule(rule)));
    }

    @Operation(summary = "更新归档规则")
    @PutMapping("/admin/archive-rules/{id}")
    public Result<ArchiveRuleVO> updateRule(
            @PathVariable Long id, @Validated @RequestBody ArchiveRuleCreateDTO dto) {
        var rule = new ArchiveRule();
        rule.setEntitySlug(dto.entitySlug());
        rule.setCondition(dto.condition());
        rule.setAfterDays(dto.afterDays() != null ? dto.afterDays() : 90);
        rule.setEnabled(dto.enabled() != null ? dto.enabled() : true);
        return Result.success(toVO(archiveService.updateRule(id, rule)));
    }

    @Operation(summary = "删除归档规则")
    @DeleteMapping("/admin/archive-rules/{id}")
    public Result<Void> deleteRule(@PathVariable Long id) {
        archiveService.deleteRule(id);
        return Result.success();
    }

    private ArchiveRuleVO toVO(ArchiveRule rule) {
        return new ArchiveRuleVO(
                rule.getId(),
                rule.getEntitySlug(),
                rule.getCondition(),
                rule.getAfterDays(),
                rule.getEnabled(),
                rule.getCreateTime());
    }
}
