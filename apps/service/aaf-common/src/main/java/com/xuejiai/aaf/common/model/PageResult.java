package com.xuejiai.aaf.common.model;

import java.io.Serializable;
import java.util.List;

/**
 * 分页响应结果。
 *
 * @param list 数据列表
 * @param total 总记录数
 * @param <T> 数据泛型
 */
public record PageResult<T>(List<T> list, long total) implements Serializable {

    public static <T> PageResult<T> empty() {
        return new PageResult<>(List.of(), 0);
    }
}
