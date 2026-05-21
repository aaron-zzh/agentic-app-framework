package com.xuejiai.aaf.common.util;

import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import org.springframework.util.StringUtils;

/** 校验工具类，提供常用格式的正则校验。 */
@UtilityClass
public class ValidationUtils {

    /** 中国大陆手机号 */
    private static final Pattern MOBILE =
            Pattern.compile("^1[3-9]\\d{9}$");

    /** HTTP/HTTPS URL */
    private static final Pattern URL =
            Pattern.compile("^https?://\\S+$");

    /** 邮箱 */
    private static final Pattern EMAIL =
            Pattern.compile("^[\\w._%+\\-]+@[\\w.\\-]+\\.[a-zA-Z]{2,}$");

    /** IPv4 */
    private static final Pattern IPV4 =
            Pattern.compile("^(\\d{1,3}\\.){3}\\d{1,3}$");

    public static boolean isMobile(String mobile) {
        return StringUtils.hasText(mobile) && MOBILE.matcher(mobile).matches();
    }

    public static boolean isUrl(String url) {
        return StringUtils.hasText(url) && URL.matcher(url).matches();
    }

    public static boolean isEmail(String email) {
        return StringUtils.hasText(email) && EMAIL.matcher(email).matches();
    }

    public static boolean isIpv4(String ip) {
        return StringUtils.hasText(ip) && IPV4.matcher(ip).matches();
    }
}
