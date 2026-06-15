package com.xuejiai.aaf.framework.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import tools.jackson.databind.json.JsonMapper;

/**
 * 验证 Jackson 3 JsonMapper 和 Jackson 2 兼容 ObjectMapper 都能正常工作。
 *
 * @author AaronZZH
 */
class JacksonTest {

    record Person(String name, int age) {}

    @Test
    void jackson3_JsonMapper_正常() throws Exception {
        var mapper = JsonMapper.builder().build();
        var json = mapper.writeValueAsString(new Person("Alice", 30));
        assertThat(json).contains("Alice");
        var person = mapper.readValue(json, Person.class);
        assertThat(person.name()).isEqualTo("Alice");
    }

    @Test
    void jackson2_ObjectMapper_兼容可用() throws Exception {
        var mapper = new ObjectMapper();
        var json = mapper.writeValueAsString(new Person("Bob", 25));
        assertThat(json).contains("Bob");
        var person = mapper.readValue(json, Person.class);
        assertThat(person.name()).isEqualTo("Bob");
    }
}
