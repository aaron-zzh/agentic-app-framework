package com.xuejiai.aaf.module.system.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import com.xuejiai.aaf.framework.security.authorization.AuthorizationService;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class AuthorizationChallengeControllerTest extends BaseMockitoUnitTest {

    @Mock private AuthorizationService authorizationService;

    @Test
    @DisplayName("Given 本人待处理 challenge When 批准 Then 仅调用批准服务")
    void should_approve_current_subject_challenge() {
        // 准备参数
        var challengeId = UUID.randomUUID();
        var controller = new AuthorizationChallengeController(authorizationService);
        when(authorizationService.approveChallenge(challengeId)).thenReturn(true);

        // 调用
        var result = controller.approve(challengeId);

        // 断言
        assertThat(result.data()).isTrue();
        verify(authorizationService).approveChallenge(challengeId);
    }

    @Test
    @DisplayName("Given challenge 控制器 When 检查公开方法 Then 不存在客户端恢复入口")
    void should_not_expose_client_resume_entry() {
        // 调用 + 断言
        assertThat(
                        Arrays.stream(AuthorizationChallengeController.class.getDeclaredMethods())
                                .map(method -> method.getName()))
                .containsExactly("approve");
    }
}
