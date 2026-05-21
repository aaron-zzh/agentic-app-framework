package com.xuejiai.aaf.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.experimental.UtilityClass;

/**
 * 金额工具类。
 *
 * <p>AAF 金额统一以「分」（整数）存储，展示时转换为「元」。
 */
@UtilityClass
public class MoneyUtils {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /**
     * 分转元（保留两位小数）。
     *
     * <p>示例：{@code fenToYuan(1999)} → {@code "19.99"}
     *
     * @param fen 金额（分）
     * @return 元字符串
     */
    public static String fenToYuan(long fen) {
        return BigDecimal.valueOf(fen).divide(HUNDRED, 2, RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * 元转分（四舍五入）。
     *
     * <p>示例：{@code yuanToFen("19.99")} → {@code 1999}
     *
     * @param yuan 元字符串
     * @return 金额（分）
     */
    public static long yuanToFen(String yuan) {
        return new BigDecimal(yuan).multiply(HUNDRED).setScale(0, RoundingMode.HALF_UP).longValue();
    }

    public static long yuanToFen(BigDecimal yuan) {
        return yuan.multiply(HUNDRED).setScale(0, RoundingMode.HALF_UP).longValue();
    }

    /**
     * 按百分比计算金额（分），四舍五入。
     *
     * <p>示例：{@code calcRate(1000, 8.5)} → {@code 85}（1000分 × 8.5% = 85分）
     *
     * @param fen  原始金额（分）
     * @param rate 百分比，如 8.5 表示 8.5%
     * @return 计算后金额（分）
     */
    public static long calcRate(long fen, double rate) {
        return BigDecimal.valueOf(fen)
                .multiply(BigDecimal.valueOf(rate))
                .divide(HUNDRED, 0, RoundingMode.HALF_UP)
                .longValue();
    }
}
