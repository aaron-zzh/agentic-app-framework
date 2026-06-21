package com.xuejiai.aaf.module.system.auth.captcha;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 阿里云 ESA AI 验证码服务端配置。
 *
 * <p>通过 {@code aaf.captcha.esa} 前缀配置：
 *
 * <ul>
 *   <li>{@code enabled}：是否启用源站校验，默认 false（兼容旧行为）。
 *   <li>{@code mode}：校验模式，目前支持：
 *       <ul>
 *         <li>{@code header-required}（默认）：仅校验请求头 {@code captcha-verify-param} 非空，假定真正的人机校验由 ESA
 *             边缘节点完成；适用于"前置 ESA 边缘 + 源站做最低限度防御"场景。
 *         <li>{@code remote}：调阿里云 ESA 服务端 API 校验 token（暂未实现， 保留扩展点）。
 *       </ul>
 * </ul>
 *
 * @author AaronZZH &amp; Kiro
 */
@ConfigurationProperties(prefix = "aaf.captcha.esa")
public record EsaCaptchaProperties(Boolean enabled, String mode) {

    public EsaCaptchaProperties {
        enabled = enabled == null ? Boolean.FALSE : enabled;
        mode = mode == null || mode.isBlank() ? "header-required" : mode;
    }
}
