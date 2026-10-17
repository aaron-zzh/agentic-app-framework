package com.xuejiai.aaf.module.voice.ws;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.xuejiai.aaf.framework.intelligent.ai.speech.SpeechService;
import com.xuejiai.aaf.framework.intelligent.ai.speech.SynthesisResult;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModel;
import com.xuejiai.aaf.module.voice.config.VoiceSessionProperties;

import reactor.core.publisher.Flux;

class VoiceRuntimeWiringTest {

    @Configuration
    static class StubSpeechConfig {

        @Bean
        SpeechService speechService() {
            return new SpeechService() {
                @Override
                public String transcribe(byte[] audioBytes, String language) {
                    return "";
                }

                @Override
                public SynthesisResult synthesize(AiModel model, String text, String voice) {
                    return new SynthesisResult(new byte[0], 0);
                }

                @Override
                public Flux<byte[]> synthesizeStream(String text, String voice) {
                    return Flux.empty();
                }
            };
        }
    }

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withUserConfiguration(
                            StubSpeechConfig.class, VoiceRuntime.class, VoiceSessionRegistry.class);

    @Test
    @DisplayName("Given 未配置任何 aaf.voice 属性 When 启动上下文 Then 运行时装配成功且使用默认值")
    void should_start_with_defaults_when_no_properties() {
        runner.run(
                context -> {
                    assertThat(context).hasNotFailed();
                    var properties = context.getBean(VoiceSessionProperties.class);
                    assertThat(properties.idleTimeoutSec()).isEqualTo(600);
                    assertThat(properties.maxActiveTurns()).isEqualTo(3);
                    assertThat(properties.maxTtsInFlight()).isEqualTo(2);
                    assertThat(properties.speculative()).isTrue();
                    assertThat(properties.voices()).isEmpty();
                });
    }

    @Test
    @DisplayName("Given 配置音色预设与较小的空闲超时 When 启动 Then 绑定生效且空闲超时不低于 30 秒")
    void should_bind_voices_and_clamp_idle_timeout() {
        runner.withPropertyValues(
                        "aaf.voice.voices.warm=longxiaochun_v3",
                        "aaf.voice.idle-timeout-sec=5",
                        "aaf.voice.speculative=false")
                .run(
                        context -> {
                            var properties = context.getBean(VoiceSessionProperties.class);
                            assertThat(properties.voices())
                                    .containsEntry("warm", "longxiaochun_v3");
                            assertThat(properties.idleTimeoutSec()).isEqualTo(30);
                            assertThat(properties.speculative()).isFalse();
                        });
    }
}
