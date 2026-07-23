package com.xuejiai.aaf.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CollectionUtils 单元测试")
class CollectionUtilsTest {

    private record Todo(Long id, Long assigneeId, String title) {}

    @Test
    @DisplayName("Given 待办分配人含空值 When 转换为列表 Then 忽略空值并保持顺序")
    void should_map_to_list_without_null_values_when_mapper_returns_null() {
        // 准备参数
        var todos =
                List.of(
                        new Todo(1L, 101L, "编写代码"),
                        new Todo(2L, null, "代码审查"),
                        new Todo(3L, 103L, "发布"));

        // 调用
        var assigneeIds = CollectionUtils.mapToList(todos, Todo::assigneeId);

        // 断言
        assertThat(assigneeIds).containsExactly(101L, 103L);
        assertThatThrownBy(() -> assigneeIds.add(104L))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("Given 待办分配人存在重复值 When 转换为集合 Then 去重并保持首次出现顺序")
    void should_map_to_set_without_duplicates_when_values_repeat() {
        // 准备参数
        var todos =
                List.of(
                        new Todo(1L, 101L, "编写代码"),
                        new Todo(2L, 102L, "代码审查"),
                        new Todo(3L, 101L, "发布"),
                        new Todo(4L, null, "归档"));

        // 调用
        var assigneeIds = CollectionUtils.mapToSet(todos, Todo::assigneeId);

        // 断言
        assertThat(assigneeIds).containsExactly(101L, 102L);
        assertThatThrownBy(() -> assigneeIds.add(103L))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("Given 待办列表 When 按标识和值转换为映射 Then 保持输入顺序")
    void should_map_to_map_when_key_and_value_mappers_are_provided() {
        // 准备参数
        var todos = List.of(new Todo(1L, 101L, "编写代码"), new Todo(2L, 102L, "代码审查"));

        // 调用
        var titlesById = CollectionUtils.mapToMap(todos, Todo::id, Todo::title);

        // 断言
        assertThat(titlesById).containsExactly(Map.entry(1L, "编写代码"), Map.entry(2L, "代码审查"));
        assertThatThrownBy(() -> titlesById.put(3L, "发布"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("Given 空来源集合 When 转换 Then 返回对应的空集合")
    void should_return_empty_collections_when_source_is_null() {
        // 调用 + 断言
        assertThat(CollectionUtils.mapToList(null, Todo::assigneeId)).isEqualTo(List.of());
        assertThat(CollectionUtils.mapToSet(null, Todo::assigneeId)).isEqualTo(Set.of());
        assertThat(CollectionUtils.mapToMap(null, Todo::id, Todo::title)).isEqualTo(Map.of());
    }

    @Test
    @DisplayName("Given 重复映射键 When 转换为映射 Then 抛出异常而不覆盖数据")
    void should_throw_when_map_contains_duplicate_keys() {
        // 准备参数
        var todos = List.of(new Todo(1L, 101L, "编写代码"), new Todo(1L, 102L, "代码审查"));

        // 调用 + 断言
        assertThatThrownBy(() -> CollectionUtils.mapToMap(todos, Todo::id, Todo::title))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Given 空映射函数 When 转换 Then 抛出空指针异常")
    void should_throw_when_mapper_is_null() {
        // 准备参数
        var todos = List.of(new Todo(1L, 101L, "编写代码"));

        // 调用 + 断言
        assertThatThrownBy(() -> CollectionUtils.mapToList(todos, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> CollectionUtils.mapToMap(todos, null, Todo::title))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> CollectionUtils.mapToMap(todos, Todo::id, null))
                .isInstanceOf(NullPointerException.class);
    }
}
