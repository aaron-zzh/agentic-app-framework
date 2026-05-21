package com.xuejiai.aaf.framework.sequence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SequenceTemplateParserTest {

    private final SequenceTemplateParser parser = new SequenceTemplateParser();

    @Test
    @DisplayName("Given null 模板 When parse Then 返回空字符串")
    void should_return_empty_when_template_null() {
        assertThat(parser.parse(null)).isEmpty();
        assertThat(parser.parse("")).isEmpty();
    }

    @Test
    @DisplayName("Given 含日期变量的模板 When parse Then 替换为当前日期")
    void should_replace_date_variables() {
        var now = LocalDateTime.now();
        var result = parser.parse("%(year)s-%(month)s-%(day)s");

        assertThat(result).isEqualTo(
                "%d-%02d-%02d".formatted(now.getYear(), now.getMonthValue(), now.getDayOfMonth()));
    }

    @Test
    @DisplayName("Given 前缀+数字+后缀 When build Then 返回完整序列号")
    void should_build_full_sequence() {
        var result = parser.build("ORD-", 1L, null, 4);
        assertThat(result).isEqualTo("ORD-0001");
    }

    @Test
    @DisplayName("Given padding=6 When build Then 数字补零到 6 位")
    void should_pad_number_to_specified_width() {
        var result = parser.build(null, 42L, null, 6);
        assertThat(result).isEqualTo("000042");
    }
}
