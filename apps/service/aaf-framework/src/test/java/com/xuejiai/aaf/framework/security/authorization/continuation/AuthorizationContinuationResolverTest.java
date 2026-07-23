package com.xuejiai.aaf.framework.security.authorization.continuation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class AuthorizationContinuationResolverTest {

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("Given 当前请求未携带 continuation 头 When 解析 Then 返回空")
    void should_return_empty_when_header_is_missing() {
        // 准备参数
        bindRequest(new MockHttpServletRequest());

        // 调用 + 断言
        assertThat(AuthorizationContinuationResolver.resolve()).isEmpty();
    }

    @Test
    @DisplayName("Given 当前请求携带合法 UUID When 解析 Then 返回 challengeId")
    void should_resolve_challenge_id_when_header_is_valid() {
        // 准备参数
        var challengeId = UUID.randomUUID();
        var request = new MockHttpServletRequest();
        request.addHeader(AuthorizationContinuationResolver.HEADER_NAME, challengeId.toString());
        bindRequest(request);

        // 调用 + 断言
        assertThat(AuthorizationContinuationResolver.resolve()).contains(challengeId);
    }

    @Test
    @DisplayName("Given continuation 已在当前请求消费 When 再次解析 Then 返回空")
    void should_return_empty_after_continuation_is_consumed() {
        // 准备参数
        var challengeId = UUID.randomUUID();
        var request = new MockHttpServletRequest();
        request.addHeader(AuthorizationContinuationResolver.HEADER_NAME, challengeId.toString());
        bindRequest(request);

        // 调用
        AuthorizationContinuationResolver.markConsumed(challengeId);

        // 断言
        assertThat(AuthorizationContinuationResolver.resolve()).isEmpty();
    }

    @Test
    @DisplayName("Given continuation 头为空缩写或非法 When 解析 Then 拒绝请求")
    void should_reject_when_header_is_invalid() {
        for (var value : new String[] {"", "1-1-1-1-1", "not-a-uuid"}) {
            // 准备参数
            var request = new MockHttpServletRequest();
            request.addHeader(AuthorizationContinuationResolver.HEADER_NAME, value);
            bindRequest(request);

            // 调用 + 断言
            assertThatThrownBy(AuthorizationContinuationResolver::resolve)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("授权 continuation 标识格式非法");
        }
    }

    private void bindRequest(MockHttpServletRequest request) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
