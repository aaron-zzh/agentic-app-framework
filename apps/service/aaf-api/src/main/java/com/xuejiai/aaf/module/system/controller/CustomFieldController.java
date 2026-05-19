package com.xuejiai.aaf.module.system.controller;

import java.util.List;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.system.service.EntityDefService;
import com.xuejiai.aaf.module.system.vo.CustomFieldAddDTO;
import com.xuejiai.aaf.module.system.vo.CustomFieldVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 自定义字段管理接口。 */
@Tag(name = "自定义字段")
@RestController
@RequestMapping("/api/entity-defs/{slug}/fields")
@RequiredArgsConstructor
public class CustomFieldController {

    private final EntityDefService entityDefService;

    @Operation(summary = "查询实体的所有字段")
    @GetMapping
    public Result<List<CustomFieldVO>> list(@PathVariable String slug) {
        return Result.success(entityDefService.listFields(slug));
    }

    @Operation(summary = "添加自定义字段")
    @PostMapping
    public Result<CustomFieldVO> add(
            @PathVariable String slug, @Validated @RequestBody CustomFieldAddDTO dto) {
        return Result.success(entityDefService.addField(slug, dto));
    }

    @Operation(summary = "隐藏自定义字段")
    @DeleteMapping("/{fieldName}")
    public Result<Void> hide(@PathVariable String slug, @PathVariable String fieldName) {
        entityDefService.hideField(slug, fieldName);
        return Result.success();
    }
}
