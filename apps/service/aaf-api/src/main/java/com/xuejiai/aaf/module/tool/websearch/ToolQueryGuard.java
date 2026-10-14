package com.xuejiai.aaf.module.tool.websearch;

import java.util.regex.Pattern;

/** 发往第三方搜索服务的查询词守卫：拦截明显的个人敏感号码。 */
public final class ToolQueryGuard {

    /** 中国大陆手机号（11 位）与 18 位身份证号。 */
    private static final Pattern SENSITIVE_NUMBER =
            Pattern.compile("(?<!\\d)(1[3-9]\\d{9}|\\d{17}[\\dXx])(?!\\d)");

    private ToolQueryGuard() {}

    /** 查询词是否包含手机号或身份证号。 */
    public static boolean containsSensitiveNumber(String query) {
        return SENSITIVE_NUMBER.matcher(query).find();
    }
}
