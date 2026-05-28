package com.xuejiai.aaf.module.system.auth.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.system.auth.service.CaptchaService;
import com.xuejiai.aaf.module.system.auth.vo.CaptchaVerifyDTO;
import com.xuejiai.aaf.module.system.auth.vo.CaptchaVO;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 图形验证码接口。
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "验证码")
@RestController
@RequestMapping("/api/system/auth/captcha")
@RequiredArgsConstructor
public class CaptchaController {

    private final CaptchaService captchaService;

    /** 生成图形验证码 */
    @GetMapping
    public Result<CaptchaVO> generate() {
        return Result.success(captchaService.generate());
    }

    /** 校验验证码 */
    @PostMapping("/verify")
    public Result<Boolean> verify(@Valid @RequestBody CaptchaVerifyDTO dto) {
        return Result.success(captchaService.verify(dto.captchaId(), dto.code()));
    }
}
