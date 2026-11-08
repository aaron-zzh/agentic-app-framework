package com.xuejiai.aaf.module.ai.aigc.voice.vo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class VoiceTtsStreamDTOTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    @DisplayName("Given 文本非空且音色为空 When 校验 Then 通过")
    void should_pass_when_text_present_and_voice_null() {
        assertThat(validator.validate(new VoiceTtsStreamDTO("你好", null))).isEmpty();
    }

    @Test
    @DisplayName("Given 文本恰好 2000 字符 When 校验 Then 通过")
    void should_pass_when_text_at_max_length() {
        assertThat(validator.validate(new VoiceTtsStreamDTO("字".repeat(2000), "v"))).isEmpty();
    }

    @Test
    @DisplayName("Given 文本为空白 When 校验 Then text 字段校验失败")
    void should_fail_when_text_blank() {
        var violations = validator.validate(new VoiceTtsStreamDTO("  ", null));

        assertThat(violations)
                .singleElement()
                .satisfies(v -> assertThat(v.getPropertyPath()).hasToString("text"));
    }

    @Test
    @DisplayName("Given 文本为 null When 校验 Then text 字段校验失败")
    void should_fail_when_text_null() {
        var violations = validator.validate(new VoiceTtsStreamDTO(null, null));

        assertThat(violations)
                .singleElement()
                .satisfies(v -> assertThat(v.getPropertyPath()).hasToString("text"));
    }

    @Test
    @DisplayName("Given 文本超过 2000 字符 When 校验 Then text 字段校验失败")
    void should_fail_when_text_too_long() {
        var violations = validator.validate(new VoiceTtsStreamDTO("字".repeat(2001), null));

        assertThat(violations)
                .singleElement()
                .satisfies(v -> assertThat(v.getPropertyPath()).hasToString("text"));
    }
}
