package com.xuejiai.aaf.common.enums;

/**
 * 可获取值数组的枚举接口。
 *
 * @param <T> 值类型
 */
public interface ArrayValuable<T> {

    /** 返回所有枚举值的数组。 */
    T[] array();
}
