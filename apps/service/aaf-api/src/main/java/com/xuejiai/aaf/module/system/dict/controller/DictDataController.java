package com.xuejiai.aaf.module.system.dict.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.system.dict.service.DictDataService;
import com.xuejiai.aaf.module.system.dict.vo.DictDataCreateDTO;
import com.xuejiai.aaf.module.system.dict.vo.DictDataUpdateDTO;
import com.xuejiai.aaf.module.system.dict.vo.DictDataVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 字典数据管理接口
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "字典数据管理")
@RestController
@RequestMapping("/api/system/dict-data")
@RequiredArgsConstructor
public class DictDataController {

    private final DictDataService dictDataService;

    @Operation(summary = "获取全部启用字典数据（前端启动时缓存用）")
    @GetMapping("/list-all-simple")
    public Result<List<DictDataVO>> listAllSimple() {
        return Result.success(dictDataService.listAll());
    }

    @Operation(summary = "按字典类型查询数据列表")
    @GetMapping("/type/{dictType}")
    public Result<List<DictDataVO>> listByType(@PathVariable String dictType) {
        return Result.success(dictDataService.listByType(dictType));
    }

    @Operation(summary = "获取字典数据详情")
    @GetMapping("/{id}")
    public Result<DictDataVO> getById(@PathVariable Long id) {
        return Result.success(dictDataService.getById(id));
    }

    @Operation(summary = "创建字典数据")
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public Result<DictDataVO> create(@Valid @RequestBody DictDataCreateDTO dto) {
        return Result.success(dictDataService.create(dto));
    }

    @Operation(summary = "更新字典数据")
    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<DictDataVO> update(
            @PathVariable Long id, @Valid @RequestBody DictDataUpdateDTO dto) {
        return Result.success(dictDataService.update(id, dto));
    }

    @Operation(summary = "删除字典数据")
    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> delete(@PathVariable Long id) {
        dictDataService.delete(id);
        return Result.success();
    }
}
