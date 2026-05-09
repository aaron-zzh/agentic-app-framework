package com.xuejiai.aaf.module.system.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.Result;

/** 验证接口，确认框架基础能力正常工作。 */
@RestController
@RequestMapping("/api")
public class HelloController {

    @GetMapping("/hello")
    public Result<String> hello(@RequestParam(defaultValue = "false") boolean error) {
        if (error) {
            throw new BusinessException(GlobalErrorCode.INTERNAL_SERVER_ERROR, "异常测试：全局异常处理正常工作");
        }
        return Result.success("Hello, AAF!");
    }
}
