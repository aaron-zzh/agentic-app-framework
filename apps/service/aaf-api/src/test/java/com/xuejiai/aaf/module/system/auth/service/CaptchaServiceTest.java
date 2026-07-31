package com.xuejiai.aaf.module.system.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class CaptchaServiceTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private CaptchaService captchaService;

    @BeforeEach
    void setUp() {
        captchaService = new CaptchaService(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("Given 正确验证码 When 校验 Then 原子消费且返回成功")
    void should_atomically_consume_code_when_verify_succeeds() {
        when(valueOperations.getAndDelete("captcha:id-1")).thenReturn("A2B3");

        var result = captchaService.verify("id-1", "a2b3");

        assertThat(result).isTrue();
        verify(valueOperations).getAndDelete("captcha:id-1");
    }

    @Test
    @DisplayName("Given 验证码已消费 When 再次校验 Then 返回失败")
    void should_reject_code_when_already_consumed() {
        when(valueOperations.getAndDelete("captcha:id-1")).thenReturn(null);

        assertThat(captchaService.verify("id-1", "A2B3")).isFalse();
    }
}
