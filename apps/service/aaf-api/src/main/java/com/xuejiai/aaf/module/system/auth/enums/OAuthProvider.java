package com.xuejiai.aaf.module.system.auth.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * OAuth 第三方登录提供商。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@RequiredArgsConstructor
public enum OAuthProvider {
    WECHAT("wechat"),
    WECOM("wecom"),
    DINGTALK("dingtalk");

    private final String code;

    /** 根据 code 查找枚举，不存在返回 null */
    public static OAuthProvider fromCode(String code) {
        for (OAuthProvider p : values()) {
            if (p.code.equals(code)) {
                return p;
            }
        }
        return null;
    }
}
