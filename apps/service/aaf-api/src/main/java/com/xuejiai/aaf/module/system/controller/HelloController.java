package com.xuejiai.aaf.module.system.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;

/** 验证接口，确认框架基础能力正常工作。 */
@RestController
@RequestMapping("/api")
public class HelloController {

    @GetMapping("/hello")
    public Result<String> hello() {
        return Result.success("Hello, AAF!");
    }
}
