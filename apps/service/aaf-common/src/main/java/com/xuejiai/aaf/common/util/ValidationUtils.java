package com.xuejiai.aaf.common.util;

import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import org.springframework.util.StringUtils;

/** 校验工具类，提供常用格式的正则校验。 */
@UtilityClass
public class ValidationUtils {

    /** 中国大陆手机号 */
    private static final Pattern PATTERN_MOBILE =
            Pattern.compile(
                    "^(?:(?:\\+|00)86)?1(?:(?:3[\\d])|(?:4[0,1,4-9])|(?:5[0-3,5-9])"
                            + "|(?:6[2,5-7])|(?:7[0-8])|(?:8[\\d])|(?:9[0-3,5-9]))\\d{8}$");

    /** HTTP/HTTPS/FTP URL */
    private static final Pattern PATTERN_URL =
            Pattern.compile(
                    "^(https?|ftp)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]");

    /** 邮箱 */
    private static final Pattern PATTERN_EMAIL =
            Pattern.compile("^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$");

    /** IPv4 */
    private static final Pattern PATTERN_IPV4 =
            Pattern.compile(
                    "^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$");

    public static boolean isMobile(String mobile) {
        return StringUtils.hasText(mobile) && PATTERN_MOBILE.matcher(mobile).matches();
    }

    public static boolean isUrl(String url) {
        return StringUtils.hasText(url) && PATTERN_URL.matcher(url).matches();
    }

    public static boolean isEmail(String email) {
        return StringUtils.hasText(email) && PATTERN_EMAIL.matcher(email).matches();
    }

    public static boolean isIpv4(String ip) {
        return StringUtils.hasText(ip) && PATTERN_IPV4.matcher(ip).matches();
    }
}
