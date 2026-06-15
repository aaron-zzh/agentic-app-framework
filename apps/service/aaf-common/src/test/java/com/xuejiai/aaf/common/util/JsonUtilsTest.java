package com.xuejiai.aaf.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

/**
 * JsonUtils 单元测试。
 *
 * @author AaronZZH
 */
class JsonUtilsTest {

    record Person(String name, int age) {}

    // ─── JsonUtils（Jackson 3 JsonMapper）─────────────────────────────────────

    @Test
    void toJsonString_正常对象() {
        var json = JsonUtils.toJsonString(new Person("Alice", 30));
        assertThat(json).contains("Alice").contains("30");
    }

    @Test
    void parseObject_正常反序列化() {
        var person = JsonUtils.parseObject("{\"name\":\"Bob\",\"age\":25}", Person.class);
        assertThat(person).isNotNull();
        assertThat(person.name()).isEqualTo("Bob");
        assertThat(person.age()).isEqualTo(25);
    }

    @Test
    void parseObject_空字符串返回null() {
        assertThat(JsonUtils.parseObject("", Person.class)).isNull();
        assertThat(JsonUtils.parseObject(null, Person.class)).isNull();
    }

    @Test
    void parseObject_非法json抛异常() {
        assertThatThrownBy(() -> JsonUtils.parseObject("not-json", Person.class))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void parseObjectQuietly_非法json返回null() {
        assertThat(JsonUtils.parseObjectQuietly("not-json", Person.class)).isNull();
    }

    @Test
    void parseArray_正常反序列化() {
        var list =
                JsonUtils.parseArray(
                        "[{\"name\":\"A\",\"age\":1},{\"name\":\"B\",\"age\":2}]", Person.class);
        assertThat(list).hasSize(2);
        assertThat(list.get(0).name()).isEqualTo("A");
    }

    @Test
    void parseArray_空字符串返回空列表() {
        assertThat(JsonUtils.parseArray("", Person.class)).isEqualTo(List.of());
    }

    // ─── Jackson 3 JsonMapper 直接使用 ───────────────────────────────────────

    @Test
    void jsonMapper_序列化反序列化() throws Exception {
        var mapper = JsonMapper.builder().build();
        var json = mapper.writeValueAsString(new Person("Charlie", 40));
        assertThat(json).contains("Charlie");
        var person = mapper.readValue(json, Person.class);
        assertThat(person.name()).isEqualTo("Charlie");
    }
}
