package com.xuejiai.aaf.module.system.dict.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.module.system.dict.domain.DictType;
import com.xuejiai.aaf.module.system.dict.service.DictTypeService;
import com.xuejiai.aaf.module.system.dict.vo.DictTypeCreateDTO;
import com.xuejiai.aaf.module.system.dict.vo.DictTypeUpdateDTO;
import com.xuejiai.aaf.module.system.dict.vo.DictTypeVO;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 字典类型管理接口。
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "字典类型管理")
@RestController
@RequestMapping("/api/system/dict-types")
@RequiredArgsConstructor
public class DictTypeController
        extends BaseCrudController<
                DictType, DictTypeVO, DictTypeCreateDTO, DictTypeUpdateDTO, PageParam> {

    private final DictTypeService dictTypeService;

    @Override
    protected DictTypeService getService() {
        return dictTypeService;
    }
}
