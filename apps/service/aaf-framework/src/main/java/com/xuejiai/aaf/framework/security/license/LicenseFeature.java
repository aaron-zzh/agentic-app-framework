package com.xuejiai.aaf.framework.security.license;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** 商业授权中的高级模块/能力码。 */
public enum LicenseFeature {
    DEVELOPER("developer", "开发者商业化模块"),
    SOURCE_DOWNLOAD("source-download", "源码包下载"),
    MANAGED_GATEWAY("managed-gateway", "托管模型网关"),
    OFFICIAL_CONSOLE("official-console", "官方服务控制台");

    private final String code;
    private final String label;

    LicenseFeature(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    public static boolean isKnown(String code) {
        return Arrays.stream(values()).anyMatch(item -> item.code.equals(code));
    }

    public static Set<String> codes() {
        return Arrays.stream(values()).map(LicenseFeature::code).collect(Collectors.toUnmodifiableSet());
    }
}
