package com.xuejiai.aaf.common.model;

import java.io.Serializable;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 分页响应结果。
 *
 * @param list 数据列表
 * @param total 总记录数
 * @param <T> 数据泛型
 */
@Schema(description = "分页结果")
public record PageResult<T>(
        @Schema(description = "数据列表") List<T> list, @Schema(description = "总记录数") long total)
        implements Serializable {

    public static <T> PageResult<T> empty() {
        return new PageResult<>(List.of(), 0);
    }
}
