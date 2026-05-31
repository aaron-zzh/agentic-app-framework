package com.xuejiai.aaf.common.model;

import java.io.Serializable;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 分页响应结果。
 *
 * @param list 数据列表
 * @param total 总记录数
 * @param pageNo 页码，从 1 开始
 * @param pageSize 每页条数
 * @param ids 当前窗口记录 ID
 * @param queryToken 查询窗口标识
 * @param fieldSet 字段集
 * @param hasMore 是否还有下一页
 * @param <T> 数据泛型
 */
@Schema(description = "分页结果")
public record PageResult<T>(
        @Schema(description = "数据列表") List<T> list,
        @Schema(description = "总记录数") long total,
        @Schema(description = "页码，从 1 开始") Integer pageNo,
        @Schema(description = "每页条数") Integer pageSize,
        @Schema(description = "当前窗口记录 ID") List<Long> ids,
        @Schema(description = "查询窗口标识") String queryToken,
        @Schema(description = "字段集") String fieldSet,
        @Schema(description = "是否还有下一页") Boolean hasMore)
        implements Serializable {

    public PageResult(List<T> list, long total) {
        this(list, total, null, null, List.of(), null, null, null);
    }

    public static <T> PageResult<T> empty() {
        return new PageResult<>(List.of(), 0);
    }
}
