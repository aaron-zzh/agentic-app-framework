package com.xuejiai.aaf.module.system.controller;

import java.util.List;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.system.service.EntityDefService;
import com.xuejiai.aaf.module.system.vo.EntityDefCreateDTO;
import com.xuejiai.aaf.module.system.vo.EntityDefUpdateDTO;
import com.xuejiai.aaf.module.system.vo.EntityDefVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 实体定义接口。 */
@Tag(name = "实体定义")
@RestController
@RequestMapping("/api/entity-defs")
@RequiredArgsConstructor
public class EntityDefController {

    private final EntityDefService entityDefService;

    @Operation(summary = "查询全量实体定义")
    @GetMapping
    public Result<List<EntityDefVO>> list() {
        return Result.success(entityDefService.listAll());
    }

    @Operation(summary = "查询单个实体定义")
    @GetMapping("/{id}")
    public Result<EntityDefVO> get(@PathVariable Long id) {
        return Result.success(entityDefService.getById(id));
    }

    @Operation(summary = "创建实体定义")
    @PostMapping
    public Result<EntityDefVO> create(@Validated @RequestBody EntityDefCreateDTO dto) {
        return Result.success(entityDefService.create(dto));
    }

    @Operation(summary = "更新实体定义")
    @PutMapping("/{id}")
    public Result<EntityDefVO> update(@PathVariable Long id, @Validated @RequestBody EntityDefUpdateDTO dto) {
        return Result.success(entityDefService.update(id, dto));
    }

    @Operation(summary = "删除实体定义")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        entityDefService.delete(id);
        return Result.success();
    }
}
