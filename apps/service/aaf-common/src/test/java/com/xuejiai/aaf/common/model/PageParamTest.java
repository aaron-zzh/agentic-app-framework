package com.xuejiai.aaf.common.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

class PageParamTest {

    @Test
    @DisplayName("Given 多字段排序 When 解析 Then 保留字段顺序和方向")
    void should_parse_ordered_sort_items_when_sort_is_valid() {
        // 准备参数
        var pageParam = new PageParam();
        pageParam.setSort("status:asc,createTime:desc");

        // 调用
        var sort = pageParam.buildSort();

        // 断言
        assertThat(sort.stream().map(Sort.Order::getProperty).toList())
                .containsExactly("status", "createTime");
        assertThat(sort.getOrderFor("status").getDirection()).isEqualTo(Sort.Direction.ASC);
        assertThat(sort.getOrderFor("createTime").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    @DisplayName("Given 未授权排序字段 When 构建分页 Then 拒绝请求")
    void should_reject_sort_field_when_not_allowlisted() {
        // 准备参数
        var pageParam = new PageParam();
        pageParam.setSort("title:asc");

        // 调用 + 断言
        assertThatThrownBy(() -> pageParam.toPageable(Sort.by("id").descending(), Set.of("status")))
                .hasMessageContaining("不支持排序字段: title");
    }

    @Test
    @DisplayName("Given 旧格式或非法格式 When 解析 Then 拒绝请求")
    void should_reject_invalid_sort_format() {
        // 准备参数
        var pageParam = new PageParam();

        // 调用 + 断言
        pageParam.setSort("-createTime");
        assertThatThrownBy(pageParam::buildSort).hasMessageContaining("排序格式非法");

        pageParam.setSort("id:asc,id:desc");
        assertThatThrownBy(pageParam::buildSort).hasMessageContaining("排序格式非法");
    }
}
