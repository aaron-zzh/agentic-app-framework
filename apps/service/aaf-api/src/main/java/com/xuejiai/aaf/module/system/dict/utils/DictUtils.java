package com.xuejiai.aaf.module.system.dict.utils;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.module.system.dict.service.DictDataService;

import lombok.RequiredArgsConstructor;

/**
 * 字典工具类，提供按 value 查 label、按 label 查 value 的便捷方法。
 *
 * <p>示例：
 *
 * <pre>{@code
 * String label = DictUtils.getLabelByValue("sys_user_sex", "1"); // "男"
 * }</pre>
 *
 * @author AaronZZH & Kiro
 */
@Component
@RequiredArgsConstructor
public class DictUtils {

    private final DictDataService dictDataService;

    /**
     * 按 value 获取 label，找不到返回 value 本身。
     *
     * @param dictType 字典类型编码
     * @param value 字典键值
     * @return 字典标签
     */
    public String getLabelByValue(String dictType, String value) {
        return dictDataService.getLabelByValue(dictType, value);
    }

    /**
     * 按 label 获取 value。
     *
     * @param dictType 字典类型编码
     * @param label 字典标签
     * @return 字典键值（Optional）
     */
    public Optional<String> getValueByLabel(String dictType, String label) {
        return dictDataService.getValueByLabel(dictType, label);
    }
}
