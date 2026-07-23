package com.xuejiai.aaf.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.xuejiai.aaf.framework.security.authorization.AuthorizationChallengeRequiredException;

class GlobalExceptionHandlerTest {

    @Test
    @DisplayName("Given 授权需要 challenge When 全局处理 Then 返回 428 并保留 challengeId")
    void should_return_precondition_required_with_challenge_id() {
        // 准备参数
        var challengeId = UUID.randomUUID();
        var handler = new GlobalExceptionHandler();

        // 调用
        var response =
                handler.handleAuthorizationChallengeRequired(
                        new AuthorizationChallengeRequiredException(challengeId));

        // 断言
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PRECONDITION_REQUIRED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(428);
        assertThat(response.getBody().data()).containsEntry("challengeId", challengeId);
    }
}
