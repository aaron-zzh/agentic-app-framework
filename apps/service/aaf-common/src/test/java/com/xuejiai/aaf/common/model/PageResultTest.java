package com.xuejiai.aaf.common.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PageResultTest {

    @Test
    @DisplayName("Given 查询窗口数据 When 创建分页结果 Then 保留窗口标识和字段集")
    void should_preserve_query_window_data_when_created() {
        var pageResult =
                new PageResult<String>(
                        List.of("todo"), 1, 1, 20, List.of(1L), "token", "list", false);

        assertThat(pageResult.ids()).containsExactly(1L);
        assertThat(pageResult.queryToken()).isEqualTo("token");
        assertThat(pageResult.fieldSet()).isEqualTo("list");
    }

    @Test
    @DisplayName("Given 普通分页 When 创建分页结果 Then 使用空窗口数据")
    void should_use_empty_window_data_when_standard_page_returns() {
        var pageResult = new PageResult<>(List.of("todo"), 1);

        assertThat(pageResult.ids()).isEmpty();
        assertThat(pageResult.queryToken()).isNull();
    }
}
