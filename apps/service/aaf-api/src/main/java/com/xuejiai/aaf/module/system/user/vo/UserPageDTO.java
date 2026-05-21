package com.xuejiai.aaf.module.system.user.vo;

import com.xuejiai.aaf.common.model.PageParam;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 用户分页查询请求。 */
@Schema(description = "用户分页查询")
@Data
@EqualsAndHashCode(callSuper = true)
public class UserPageDTO extends PageParam {

    @Schema(description = "用户名，模糊匹配")
    private String username;

    @Schema(description = "昵称，模糊匹配")
    private String nickname;

    @Schema(description = "状态")
    private Integer status;
}
