package com.xuejiai.aaf.framework.crud.export;

import com.xuejiai.aaf.framework.crud.BaseCrudService;

/**
 * 字典 label 解析 SPI。由 {@code aaf-api} 的字典模块实现并注册为 Spring Bean， {@link BaseCrudService} 通过 {@code
 * ObjectProvider} 可选注入，实现导出时字典 value → label 转换。
 *
 * <p>{@code aaf-framework} 不允许依赖 {@code aaf-api}，故以 SPI 形式解耦。
 *
 * @author AaronZZH & Kiro
 */
public interface DictLabelResolver {

    /** 按字典类型将 value 解析为 label，查不到时返回 value 本身。 */
    String resolve(String dictType, String value);
}
