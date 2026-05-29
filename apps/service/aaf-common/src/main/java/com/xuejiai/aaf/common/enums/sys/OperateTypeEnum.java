package com.xuejiai.aaf.common.enums.sys;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 操作类型枚举，对应字典 sys_operate_type，用于操作日志。 */
@Getter
@AllArgsConstructor
public enum OperateTypeEnum implements ArrayValuable<Integer> {
    OTHER(0, "其它"),
    QUERY(1, "查询"),
    CREATE(2, "新增"),
    UPDATE(3, "修改"),
    DELETE(4, "删除"),
    EXPORT(5, "导出"),
    IMPORT(6, "导入");

    private final Integer code;
    private final String label;

    public static final Integer[] ARRAYS =
            Arrays.stream(values()).map(OperateTypeEnum::getCode).toArray(Integer[]::new);

    @Override
    public Integer[] array() {
        return ARRAYS;
    }
}
