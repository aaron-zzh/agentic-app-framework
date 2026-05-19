package com.xuejiai.aaf.module.system.controller;

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
import com.xuejiai.aaf.module.system.domain.DataAccessRule;
import com.xuejiai.aaf.module.system.service.DataAccessService;
import com.xuejiai.aaf.module.system.vo.DataAccessRuleCreateDTO;
import com.xuejiai.aaf.module.system.vo.DataAccessRuleVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 行级数据权限规则管理接口。 */
@Tag(name = "数据权限规则管理")
@RestController
@RequestMapping("/api/admin/data-access-rules")
@RequiredArgsConstructor
public class DataAccessRuleController {

    private final DataAccessService dataAccessService;

    @Operation(summary = "获取规则列表")
    @GetMapping
    public Result<List<DataAccessRuleVO>> list() {
        return Result.success(dataAccessService.list().stream().map(this::toVO).toList());
    }

    @Operation(summary = "获取规则详情")
    @GetMapping("/{id}")
    public Result<DataAccessRuleVO> get(@PathVariable Long id) {
        return Result.success(toVO(dataAccessService.getById(id)));
    }

    @Operation(summary = "创建规则")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result<DataAccessRuleVO> create(@Validated @RequestBody DataAccessRuleCreateDTO dto) {
        var rule = new DataAccessRule();
        rule.setEntitySlug(dto.entitySlug());
        rule.setRoles(dto.roles());
        rule.setCondition(dto.condition());
        rule.setEffect(dto.effect() != null ? dto.effect() : "allow");
        return Result.success(toVO(dataAccessService.create(rule)));
    }

    @Operation(summary = "更新规则")
    @PutMapping("/{id}")
    public Result<DataAccessRuleVO> update(
            @PathVariable Long id, @Validated @RequestBody DataAccessRuleCreateDTO dto) {
        var rule = new DataAccessRule();
        rule.setEntitySlug(dto.entitySlug());
        rule.setRoles(dto.roles());
        rule.setCondition(dto.condition());
        rule.setEffect(dto.effect() != null ? dto.effect() : "allow");
        return Result.success(toVO(dataAccessService.update(id, rule)));
    }

    @Operation(summary = "删除规则")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        dataAccessService.delete(id);
        return Result.success();
    }

    private DataAccessRuleVO toVO(DataAccessRule rule) {
        return new DataAccessRuleVO(
                rule.getId(),
                rule.getEntitySlug(),
                rule.getRoles(),
                rule.getCondition(),
                rule.getEffect(),
                rule.getCreateTime());
    }
}
