package com.xuejiai.aaf.framework.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

/** 验证 Jackson 3 JsonMapper 正常工作。 */
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
}
