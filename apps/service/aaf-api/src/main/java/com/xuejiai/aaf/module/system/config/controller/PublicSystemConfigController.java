package com.xuejiai.aaf.module.system.config.controller;

import java.util.Set;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.constant.SysConfigKeys;
import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.system.config.service.SystemConfigService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 系统配置公开接口（无需登录）。
 *
 * <p>挂在 {@code /api/public/system/configs/**}，由 SecurityConfig.PUBLIC_PATHS 中的 {@code
 * /api/public/**} 放行。
 *
 * <p>仅返回 {@link #PUBLIC_KEYS} 显式声明的配置项 value，避免敏感配置外泄。 非白名单 key 一律返回 404，不区分"不存在"与"非公开"以防接口探测。
 *
 * @author AaronZZH &amp; Kiro
 */
@Tag(name = "系统配置（公开）")
@RestController
@RequestMapping("/api/public/system/configs")
@RequiredArgsConstructor
public class PublicSystemConfigController {

    /** 允许公开访问的 sys_config.config_key 集合 */
    private static final Set<String> PUBLIC_KEYS = Set.of(
            SysConfigKeys.Member.FAQ,
            SysConfigKeys.Contact.WECHAT_QR_IMAGE);

    private final SystemConfigService configService;

    @Operation(summary = "按 key 获取公开配置项的 value（仅白名单 key）")
    @GetMapping("/{key}")
    public Result<String> get(@PathVariable String key) {
        if (!PUBLIC_KEYS.contains(key)) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "配置项不存在: " + key);
        }
        var value = configService.getString(key);
        if (value == null) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "配置项不存在: " + key);
        }
        return Result.success(value);
    }
}
