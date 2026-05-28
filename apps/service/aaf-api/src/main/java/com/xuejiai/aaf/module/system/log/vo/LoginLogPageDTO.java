package com.xuejiai.aaf.module.system.log.vo;

import java.time.LocalDateTime;

import com.xuejiai.aaf.common.model.PageParam;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 登录日志分页查询请求。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "登录日志分页查询")
@Data
@EqualsAndHashCode(callSuper = true)
public class LoginLogPageDTO extends PageParam {

    @Schema(description = "用户名，模糊匹配")
    private String username;

    @Schema(description = "登录 IP")
    private String ip;

    @Schema(description = "是否成功")
    private Boolean success;

    @Schema(description = "开始时间")
    private LocalDateTime startTime;

    @Schema(description = "结束时间")
    private LocalDateTime endTime;
}
