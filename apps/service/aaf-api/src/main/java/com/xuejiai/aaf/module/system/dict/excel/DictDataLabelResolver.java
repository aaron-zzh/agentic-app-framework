package com.xuejiai.aaf.module.system.dict.excel;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.crud.export.DictLabelResolver;
import com.xuejiai.aaf.module.system.dict.service.DictDataService;

import lombok.RequiredArgsConstructor;

/**
 * {@link DictLabelResolver} 的字典模块实现，供 {@code BaseCrudService} 通用导出时解析字典 label。
 *
 * @author AaronZZH & Kiro
 */
@Component
@RequiredArgsConstructor
public class DictDataLabelResolver implements DictLabelResolver {

    private final DictDataService dictDataService;

    @Override
    public String resolve(String dictType, String value) {
        return dictDataService.getLabelByValue(dictType, value);
    }
}
