package com.xuejiai.aaf.module.channel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.client.RestClient;

import com.xuejiai.aaf.module.channel.domain.WebhookConfig;
import com.xuejiai.aaf.module.channel.repository.WebhookConfigRepository;
import com.xuejiai.aaf.module.channel.repository.WebhookLogRepository;

@ExtendWith(MockitoExtension.class)
class WebhookServiceTest {

    @Mock private WebhookConfigRepository configRepository;
    @Mock private WebhookLogRepository logRepository;
    @Mock private RestClient.Builder restClientBuilder;
    @Mock private ChannelMessageRouter router;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private WebhookService webhookService;

    @BeforeEach
    void setUp() {
        webhookService =
                new WebhookService(
                        configRepository, logRepository, restClientBuilder, router, redisTemplate);
    }

    @Test
    @DisplayName("Given 缺少签名或 nonce When 校验入站 webhook Then 失败关闭且不查配置")
    void should_fail_closed_when_signature_headers_missing() {
        assertThat(webhookService.verifyInboundSignature(1L, null, "1", "nonce", "{}")).isFalse();
        assertThat(webhookService.verifyInboundSignature(1L, "sig", "1", "", "{}")).isFalse();
        verify(configRepository, never()).findById(1L);
    }

    @Test
    @DisplayName("Given 有效签名 When 相同 nonce 重放 Then 首次成功且第二次失败")
    void should_reject_replayed_nonce_when_signature_valid() throws Exception {
        var timestamp = String.valueOf(Instant.now().getEpochSecond());
        var nonce = "nonce-1";
        var body = "{\"event\":\"created\"}";
        var secret = "secret";
        var config = new WebhookConfig();
        config.setStatus("active");
        config.setSecret(secret);
        when(configRepository.findById(1L)).thenReturn(java.util.Optional.of(config));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent("webhook:replay:1:nonce-1", "1", Duration.ofMinutes(5)))
                .thenReturn(true, false);
        var signature = hmac("%s\n%s\n%s".formatted(timestamp, nonce, body), secret);

        assertThat(webhookService.verifyInboundSignature(1L, signature, timestamp, nonce, body))
                .isTrue();
        assertThat(webhookService.verifyInboundSignature(1L, signature, timestamp, nonce, body))
                .isFalse();
    }

    @Test
    @DisplayName("Given 过期时间戳 When 校验入站 webhook Then 拒绝且不消费 nonce")
    void should_reject_expired_timestamp_when_verify() {
        var timestamp = String.valueOf(Instant.now().minusSeconds(301).getEpochSecond());

        assertThat(webhookService.verifyInboundSignature(1L, "sig", timestamp, "nonce", "{}"))
                .isFalse();
        verify(redisTemplate, never()).opsForValue();
    }

    private String hmac(String data, String secret) throws Exception {
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return java.util.HexFormat.of()
                .formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }
}
