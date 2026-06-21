package com.xuejiai.aaf.module.system.dict.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.module.system.dict.domain.DictData;
import com.xuejiai.aaf.module.system.dict.service.DictDataService;
import com.xuejiai.aaf.module.system.dict.vo.DictDataCreateDTO;
import com.xuejiai.aaf.module.system.dict.vo.DictDataUpdateDTO;
import com.xuejiai.aaf.module.system.dict.vo.DictDataVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 字典数据管理接口。
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "字典数据管理")
@RestController
@RequestMapping("/api/system/dict-data")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class DictDataController
        extends BaseCrudController<
                DictData, DictDataVO, DictDataCreateDTO, DictDataUpdateDTO, PageParam> {

    private final DictDataService dictDataService;

    @Override
    protected DictDataService getService() {
        return dictDataService;
    }

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
}
