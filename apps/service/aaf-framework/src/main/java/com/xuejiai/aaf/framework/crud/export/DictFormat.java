package com.xuejiai.aaf.framework.crud.export;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.xuejiai.aaf.framework.crud.BaseCrudService;

/**
 * 标注字段导出 Excel 时使用字典进行 value → label 转换。
 *
 * <p>由 {@link BaseCrudService} 的通用导出（{@code exportSheet}）反射读取，通过 {@link DictLabelResolver} 解析
 * label，不依赖 Fesod 的 {@code @ExcelProperty(converter=...)} 机制。
 *
 * <p>示例：
 *
 * <pre>{@code
 * @DictFormat(DictType.Sys.TODO_CATEGORY)
 * private String category;
 * }</pre>
 *
 * @author AaronZZH & Kiro
 */
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DictFormat {

    /** 字典类型编码，参见 {@code com.xuejiai.aaf.common.constant.DictType}。 */
    String value();
}
