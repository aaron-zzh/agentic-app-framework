package com.xuejiai.aaf.module.system.auth.captcha;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 阿里云 ESA AI 验证码源站校验。
 *
 * <p>本类作为最低限度的源站防御层，避开"边缘节点被绕过 → 源站裸奔"的情况。 实际人机校验由 ESA 边缘节点完成，源站只对 {@code captcha-verify-param}
 * 请求头做必填/格式校验。如需调阿里云服务端 API 二次校验 token，扩展 {@link #verifyRemote(String)} 即可。
 *
 * <p>校验调用点：登录 / 注册 / 发邮箱验证码 / 发短信验证码 4 个公开接口。
 *
 * @author AaronZZH &amp; Kiro
 */
@Slf4j
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(EsaCaptchaProperties.class)
public class EsaCaptchaVerifier {

    /** 请求头名称：与前端 {@code authApi} 保持一致 */
    public static final String HEADER_NAME = "captcha-verify-param";

    private final EsaCaptchaProperties properties;

    /**
     * 校验请求头，校验失败抛 {@link BusinessException}（FORBIDDEN）。
     *
     * @param captchaVerifyParam 来自 {@code @RequestHeader("captcha-verify-param")}，可空
     * @param scene 当前接口业务场景，仅用于日志与排查（如 "login" / "send-sms-code"）
     */
    public void verify(String captchaVerifyParam, String scene) {
        if (!Boolean.TRUE.equals(properties.enabled())) {
            // 开关关闭：跳过校验，保持兼容；同时打 debug 日志便于排查
            log.debug("ESA captcha disabled, skip verify scene={}", scene);
            return;
        }

        String mode = properties.mode();
        switch (mode) {
            case "header-required" -> verifyHeaderRequired(captchaVerifyParam, scene);
            case "remote" -> verifyRemote(captchaVerifyParam);
            default -> {
                log.warn("Unknown ESA captcha mode={}, fallback to header-required", mode);
                verifyHeaderRequired(captchaVerifyParam, scene);
            }
        }
    }

    private void verifyHeaderRequired(String captchaVerifyParam, String scene) {
        if (captchaVerifyParam == null || captchaVerifyParam.isBlank()) {
            log.warn("ESA captcha verify failed: missing header, scene={}", scene);
            throw new BusinessException(GlobalErrorCode.FORBIDDEN, "人机校验失败，请刷新页面重试");
        }
    }

    /**
     * 调阿里云 ESA 服务端 API 校验 token。当前未实现，留扩展点。
     *
     * <p>实现思路：引入阿里云 captcha SDK，调 {@code VerifyIntelligentCaptcha} 或 等价 OpenAPI，根据返回码判定。需读取
     * AccessKeyId/Secret 等敏感配置。
     */
    private void verifyRemote(String captchaVerifyParam) {
        if (captchaVerifyParam == null || captchaVerifyParam.isBlank()) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN, "人机校验失败，请刷新页面重试");
        }
        log.warn(
                "ESA captcha remote mode not implemented yet, treat as passed; "
                        + "implement aliyun SDK call when needed");
    }
}
