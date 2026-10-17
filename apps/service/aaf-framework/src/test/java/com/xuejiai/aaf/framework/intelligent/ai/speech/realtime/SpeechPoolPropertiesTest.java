package com.xuejiai.aaf.framework.intelligent.ai.speech.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class SpeechPoolPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(SpeechPoolProperties.class)
    static class PropertiesConfig {}

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(PropertiesConfig.class);

    @Test
    @DisplayName("Given 无配置 When 绑定 Then 默认值满足 asr+tts 不超过 SDK 默认连接池 32")
    void should_use_defaults_within_default_connection_pool() {
        runner.run(
                context -> {
                    var properties = context.getBean(SpeechPoolProperties.class);
                    assertThat(properties.asrSize() + properties.ttsSize())
                            .isLessThanOrEqualTo(SpeechPoolProperties.DEFAULT_CONNECTION_POOL_SIZE);
                    assertThat(properties.borrowTimeoutMs()).isEqualTo(300);
                    assertThat(properties.idleEvictSeconds()).isLessThan(60);
                    properties.validate(SpeechPoolProperties.DEFAULT_CONNECTION_POOL_SIZE);
                });
    }

    @Test
    @DisplayName("Given 自定义池大小 When 绑定 Then 属性生效")
    void should_bind_custom_sizes() {
        runner.withPropertyValues("aaf.speech.pool.asr-size=8", "aaf.speech.pool.tts-size=24")
                .run(
                        context -> {
                            var properties = context.getBean(SpeechPoolProperties.class);
                            assertThat(properties.asrSize()).isEqualTo(8);
                            assertThat(properties.ttsSize()).isEqualTo(24);
                        });
    }
}
