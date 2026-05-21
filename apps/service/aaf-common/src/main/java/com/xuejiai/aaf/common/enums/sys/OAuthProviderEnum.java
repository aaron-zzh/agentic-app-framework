package com.xuejiai.aaf.common.enums.sys;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** OAuth 提供商枚举，对应字典 sys_oauth_provider。 */
@Getter
@AllArgsConstructor
public enum OAuthProviderEnum {
    GITHUB("github", "GitHub"),
    GOOGLE("google", "Google"),
    WECHAT("wechat", "微信"),
    DINGTALK("dingtalk", "钉钉");

    private final String code;
    private final String label;
}
