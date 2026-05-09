package com.xuejiai.aaf.module.system.vo;

import com.xuejiai.aaf.common.model.PageParam;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 用户分页查询请求。 */
public record UserPageDTO(
        String username,
        String nickname,
        Short status,
        @NotNull(message = "页码不能为空") @Min(1) int pageNo,
        @NotNull(message = "每页条数不能为空") @Min(1) @Max(200) int pageSize) {

    public PageParam toPageParam() {
        return new PageParam(pageNo, pageSize);
    }
}
