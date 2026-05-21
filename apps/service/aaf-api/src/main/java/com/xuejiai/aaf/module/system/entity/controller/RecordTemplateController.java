package com.xuejiai.aaf.module.system.entity.controller;

import java.util.List;

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

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.system.entity.service.RecordTemplateService;
import com.xuejiai.aaf.module.system.entity.vo.RecordTemplateCreateDTO;
import com.xuejiai.aaf.module.system.entity.vo.RecordTemplateVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 记录模板接口。 */
@Tag(name = "记录模板")
@RestController
@RequestMapping("/api/system/record-templates")
@RequiredArgsConstructor
public class RecordTemplateController {

    private final RecordTemplateService recordTemplateService;

    @Operation(summary = "查询实体下可见模板列表")
    @GetMapping
    public Result<List<RecordTemplateVO>> list(
            @RequestParam String entitySlug, @RequestParam Long userId) {
        return Result.success(recordTemplateService.listBySlug(entitySlug, userId));
    }

    @Operation(summary = "创建模板")
    @PostMapping
    public Result<RecordTemplateVO> create(@Validated @RequestBody RecordTemplateCreateDTO dto) {
        return Result.success(recordTemplateService.create(dto));
    }

    @Operation(summary = "更新模板")
    @PutMapping("/{id}")
    public Result<RecordTemplateVO> update(
            @PathVariable Long id, @Validated @RequestBody RecordTemplateCreateDTO dto) {
        return Result.success(recordTemplateService.update(id, dto));
    }

    @Operation(summary = "复制模板")
    @PostMapping("/{id}/copy")
    public Result<RecordTemplateVO> copy(@PathVariable Long id) {
        return Result.success(recordTemplateService.copy(id));
    }

    @Operation(summary = "设为默认模板")
    @PutMapping("/{id}/default")
    public Result<Void> setDefault(@PathVariable Long id, @RequestParam Long userId) {
        recordTemplateService.setDefault(id, userId);
        return Result.success();
    }

    @Operation(summary = "删除模板")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        recordTemplateService.delete(id);
        return Result.success();
    }
}
