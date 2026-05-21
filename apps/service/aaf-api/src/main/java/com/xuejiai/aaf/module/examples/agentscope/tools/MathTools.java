package com.xuejiai.aaf.module.examples.agentscope.tools;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

/** 数学与时间工具集，演示 @Tool/@ToolParam 注解用法。 */
@Component
@ConditionalOnProperty(
        name = "aaf.examples.agentscope.enabled",
        havingValue = "true",
        matchIfMissing = false)
public class MathTools {

    /** 四则运算计算器 */
    @Tool(name = "calculate", description = "计算简单的四则运算表达式，如 '12 + 34'、'10 * 5'")
    public String calculate(
            @ToolParam(name = "expression", description = "数学表达式，支持 +、-、*、/") String expression) {
        try {
            String expr = expression.replaceAll("\\s+", "");
            double result;
            if (expr.contains("+")) {
                String[] p = expr.split("\\+", 2);
                result = Double.parseDouble(p[0]) + Double.parseDouble(p[1]);
            } else if (expr.contains("-")) {
                String[] p = expr.split("-", 2);
                result = Double.parseDouble(p[0]) - Double.parseDouble(p[1]);
            } else if (expr.contains("\\*")) {
                String[] p = expr.split("\\*", 2);
                result = Double.parseDouble(p[0]) * Double.parseDouble(p[1]);
            } else if (expr.contains("/")) {
                String[] p = expr.split("/", 2);
                result = Double.parseDouble(p[0]) / Double.parseDouble(p[1]);
            } else {
                return "不支持的运算符，请使用 +、-、*、/";
            }
            return expression + " = " + result;
        } catch (Exception e) {
            return "计算失败：" + e.getMessage();
        }
    }

    /** 查询指定时区的当前时间 */
    @Tool(name = "get_current_time", description = "获取指定时区的当前时间")
    public String getCurrentTime(
            @ToolParam(name = "timezone", description = "时区名称，如 'Asia/Shanghai'、'America/New_York'")
                    String timezone) {
        try {
            ZoneId zoneId = ZoneId.of(timezone);
            String time =
                    LocalDateTime.now(zoneId)
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            return timezone + " 当前时间：" + time;
        } catch (Exception e) {
            return "无效时区：" + timezone;
        }
    }
}
