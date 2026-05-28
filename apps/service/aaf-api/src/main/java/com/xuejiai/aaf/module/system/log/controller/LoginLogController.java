package com.xuejiai.aaf.module.system.log.controller;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.system.log.service.LoginLogService;
import com.xuejiai.aaf.module.system.log.vo.LoginLogPageDTO;
import com.xuejiai.aaf.module.system.log.vo.LoginLogVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 登录日志查询接口。
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "登录日志")
@RestController
@RequestMapping("/api/system/login-logs")
@RequiredArgsConstructor
public class LoginLogController {

    private final LoginLogService loginLogService;

    @Operation(summary = "分页查询登录日志", description = "支持 username/ip/success/时间范围筛选")
    @GetMapping
    public Result<PageResult<LoginLogVO>> page(@Validated @ParameterObject LoginLogPageDTO req) {
        return Result.success(loginLogService.page(req));
    }
}
