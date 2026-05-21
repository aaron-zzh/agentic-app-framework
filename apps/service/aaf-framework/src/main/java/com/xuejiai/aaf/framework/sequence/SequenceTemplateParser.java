package com.xuejiai.aaf.framework.sequence;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

/** 序列号模板解析，支持前缀/后缀中的日期变量。 */
@Component
public class SequenceTemplateParser {

    public String parse(String template) {
        if (template == null || template.isBlank()) return "";
        var now = LocalDateTime.now();
        return template
                .replace("%(year)s", String.valueOf(now.getYear()))
                .replace("%(y)s", String.format("%02d", now.getYear() % 100))
                .replace("%(month)s", String.format("%02d", now.getMonthValue()))
                .replace("%(day)s", String.format("%02d", now.getDayOfMonth()))
                .replace("%(doy)s", String.format("%03d", now.getDayOfYear()))
                .replace("%(woy)s", String.format("%02d", now.getDayOfYear() / 7 + 1));
    }

    public String build(String prefix, Long number, String suffix, int padding) {
        return parse(prefix) + String.format("%0" + padding + "d", number) + parse(suffix);
    }
}
