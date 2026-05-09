package com.xuejiai.aaf.common.model;

import java.io.Serializable;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 分页请求参数。
 *
 * @param pageNo 页码，从 1 开始
 * @param pageSize 每页条数，最大 200
 */
public record PageParam(
        @NotNull(message = "页码不能为空") @Min(value = 1, message = "页码最小值为 1") int pageNo,
        @NotNull(message = "每页条数不能为空")
                @Min(value = 1, message = "每页条数最小值为 1")
                @Max(value = 200, message = "每页条数最大值为 200")
                int pageSize)
        implements Serializable {

    /** 默认分页参数 */
    public static final PageParam DEFAULT = new PageParam(1, 10);
}
