package com.xuejiai.aaf.framework.logging;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 操作日志脱敏（M51）。
 *
 * <p>修复前：切面把 {@code Arrays.toString(args)} 与 {@code result.toString()} 原样写入审计日志，
 * 登录/改密/绑定/发短信等接口的密码、token、密钥、身份证会明文落库，审计表本身成了敏感数据泄漏点。
 *
 * <p>两层防护：
 *
 * <ol>
 *   <li>按**参数名**脱敏——命中敏感词的整个入参直接替换为掩码（方法参数名在 {@code -parameters} 编译下可得）
 *   <li>按**文本模式**脱敏——对渲染后的字符串再扫一遍 {@code key=value} / {@code "key":"value"} 形态，覆盖嵌套对象 toString 与
 *       JSON 出参
 * </ol>
 *
 * <p>只做脱敏，不改变日志结构；宁可多掩一个字段，也不让敏感值进审计表。
 */
public final class SensitiveLogMasker {

    /** 掩码占位符 */
    public static final String MASK = "***";

    /** 敏感字段/参数名关键字（小写匹配，包含即命中） */
    private static final Set<String> SENSITIVE_KEYWORDS =
            Set.of(
                    "password",
                    "passwd",
                    "pwd",
                    "oldpassword",
                    "newpassword",
                    "secret",
                    "token",
                    "accesstoken",
                    "refreshtoken",
                    "apikey",
                    "apisecret",
                    "accesskey",
                    "secretkey",
                    "privatekey",
                    "credential",
                    "authorization",
                    "idcard",
                    "idnumber",
                    "bankcard",
                    "cvv",
                    "captcha",
                    "verifycode",
                    "smscode");

    /**
     * {@code key=value} 与 {@code "key":"value"} 两种形态的敏感值匹配。
     *
     * <p>值的终止符取 {@code , } {@code }} {@code ]} 与空白，覆盖 record/lombok 的 toString 与 JSON。
     */
    private static final Pattern KEY_VALUE =
            Pattern.compile(
                    "(?i)([\"']?)(\\w*(?:password|passwd|pwd|secret|token|apikey|apisecret|accesskey|secretkey|privatekey|credential|authorization|idcard|idnumber|bankcard|cvv|captcha|verifycode|smscode)\\w*)\\1\\s*([=:])\\s*(\"[^\"]*\"|'[^']*'|[^,}\\]\\s]+)");

    private SensitiveLogMasker() {}

    /** 参数名是否敏感（整值掩码）。 */
    public static boolean isSensitiveName(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        var lower = name.toLowerCase(Locale.ROOT);
        return SENSITIVE_KEYWORDS.stream().anyMatch(lower::contains);
    }

    /** 对任意日志文本做模式脱敏。 */
    public static String maskText(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        return KEY_VALUE.matcher(text).replaceAll(mr -> mr.group(2) + mr.group(3) + MASK);
    }

    /**
     * 渲染方法入参：参数名命中敏感词的整值掩码，其余渲染后再做一次模式脱敏。
     *
     * @param parameterNames 方法参数名（编译未保留参数名时可为 null）
     * @param args 实参
     */
    public static String maskArguments(String[] parameterNames, Object[] args) {
        if (args == null || args.length == 0) {
            return "[]";
        }
        var sb = new StringBuilder("[");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            var name =
                    parameterNames != null && i < parameterNames.length ? parameterNames[i] : null;
            if (isSensitiveName(name)) {
                sb.append(name).append('=').append(MASK);
                continue;
            }
            if (name != null) {
                sb.append(name).append('=');
            }
            sb.append(render(args[i]));
        }
        return maskText(sb.append(']').toString());
    }

    /** 渲染单个值——跳过 Web 容器对象，避免把请求/响应整体打进日志。 */
    private static String render(Object value) {
        if (value == null) {
            return "null";
        }
        var typeName = value.getClass().getName();
        if (typeName.startsWith("jakarta.servlet")
                || typeName.startsWith("org.springframework.web.multipart")
                || typeName.startsWith("org.springframework.http")) {
            return "<" + value.getClass().getSimpleName() + ">";
        }
        return String.valueOf(value);
    }
}
