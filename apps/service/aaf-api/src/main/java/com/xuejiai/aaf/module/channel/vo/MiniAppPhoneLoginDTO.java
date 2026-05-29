package com.xuejiai.aaf.module.channel.vo;

import jakarta.validation.constraints.NotBlank;

/**
 * 微信小程序手机号一键登录请求。
 *
 * @param phoneCode wx.getPhoneNumber 获取的 code，用于换取手机号
 * @param loginCode wx.login 获取的 code，用于换取 openid
 */
public record MiniAppPhoneLoginDTO(
        @NotBlank String phoneCode,
        @NotBlank String loginCode) {}
