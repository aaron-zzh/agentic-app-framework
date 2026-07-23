package com.xuejiai.aaf.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MapUtils 单元测试")
class MapUtilsTest {

    @Test
    @DisplayName("Given 存在非空映射值 When 查找并处理 Then 消费该值")
    void should_consume_value_when_mapping_exists() {
        // 准备参数
        var values = Map.of("assignee", "张三");
        var consumed = new AtomicReference<String>();

        // 调用
        MapUtils.findAndThen(values, "assignee", consumed::set);

        // 断言
        assertThat(consumed).hasValue("张三");
    }

    @Test
    @DisplayName("Given 映射或键无效 When 查找并处理 Then 不消费任何值")
    void should_not_consume_value_when_mapping_or_key_is_invalid() {
        // 准备参数
        var values = new java.util.HashMap<String, String>();
        values.put("empty", null);
        var consumed = new AtomicReference<String>();

        // 调用
        MapUtils.findAndThen(null, "assignee", consumed::set);
        MapUtils.findAndThen(values, null, consumed::set);
        MapUtils.findAndThen(values, "missing", consumed::set);
        MapUtils.findAndThen(values, "empty", consumed::set);

        // 断言
        assertThat(consumed).hasNullValue();
    }

    @Test
    @DisplayName("Given 空消费者 When 查找并处理 Then 立即抛出空指针异常")
    void should_throw_when_consumer_is_null() {
        // 调用 + 断言
        assertThatThrownBy(() -> MapUtils.findAndThen(null, null, null))
                .isInstanceOf(NullPointerException.class);
    }
}
