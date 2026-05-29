package com.xuejiai.aaf.module.channel.vo;

import jakarta.validation.constraints.NotBlank;

/**
 * 小程序登录请求。
 *
 * @param code wx.login() 获取的临时登录凭证
 */
public record MiniAppLoginDTO(@NotBlank String code) {}
