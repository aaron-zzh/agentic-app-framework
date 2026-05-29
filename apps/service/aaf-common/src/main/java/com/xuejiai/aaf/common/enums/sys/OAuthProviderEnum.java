package com.xuejiai.aaf.common.enums.sys;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** OAuth 提供商枚举，对应字典 sys_oauth_provider。 */
@Getter
@AllArgsConstructor
public enum OAuthProviderEnum implements ArrayValuable<String> {
    GITHUB("github", "GitHub"),
    GOOGLE("google", "Google"),
    WECHAT("wechat", "微信"),
    WECOM("wecom", "企业微信"),
    DINGTALK("dingtalk", "钉钉");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(OAuthProviderEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
