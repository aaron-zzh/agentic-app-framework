package com.xuejiai.aaf.module.system.dict.controller;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.system.dict.service.DictTypeService;
import com.xuejiai.aaf.module.system.dict.vo.DictTypeCreateDTO;
import com.xuejiai.aaf.module.system.dict.vo.DictTypeUpdateDTO;
import com.xuejiai.aaf.module.system.dict.vo.DictTypeVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "字典类型管理")
@RestController
@RequestMapping("/api/system/dict-types")
@RequiredArgsConstructor
public class DictTypeController {

    private final DictTypeService dictTypeService;

    @Operation(summary = "获取字典类型列表")
    @GetMapping
    public Result<List<DictTypeVO>> list() {
        return Result.success(dictTypeService.list());
    }

    @Operation(summary = "获取字典类型详情")
    @GetMapping("/{id}")
    public Result<DictTypeVO> getById(@PathVariable Long id) {
        return Result.success(dictTypeService.getById(id));
    }

    @Operation(summary = "创建字典类型")
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public Result<DictTypeVO> create(@Valid @RequestBody DictTypeCreateDTO dto) {
        return Result.success(dictTypeService.create(dto));
    }

    @Operation(summary = "更新字典类型")
    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<DictTypeVO> update(@PathVariable Long id, @Valid @RequestBody DictTypeUpdateDTO dto) {
        return Result.success(dictTypeService.update(id, dto));
    }

    @Operation(summary = "删除字典类型")
    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> delete(@PathVariable Long id) {
        dictTypeService.delete(id);
        return Result.success();
    }
}
