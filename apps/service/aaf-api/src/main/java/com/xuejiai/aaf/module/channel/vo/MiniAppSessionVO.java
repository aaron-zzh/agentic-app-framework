package com.xuejiai.aaf.module.channel.vo;

/**
 * 小程序登录响应。
 *
 * @param accessToken JWT 令牌
 * @param openid 微信 openid
 * @param userId 系统用户 ID
 */
public record MiniAppSessionVO(String accessToken, String openid, Long userId) {}
