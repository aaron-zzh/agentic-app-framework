package com.xuejiai.aaf.framework.security.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.security.core.Authentication;

import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class AuthorizationMethodSecurityAdapterTest extends BaseMockitoUnitTest {

    @Mock private AuthorizationService authorizationService;
    @Mock private Authentication authentication;

    @Test
    @DisplayName("Given 领域对象与权限码 When 方法安全检查 Then 构造 L1 请求并委托唯一授权门面")
    void should_delegate_domain_object_overload_to_authorization_service() {
        when(authorizationService.authorize(any())).thenReturn(allowDecision());
        var adapter = new AuthorizationMethodSecurityAdapter(authorizationService);

        var allowed = adapter.hasPermission(authentication, 42L, "todo:read");

        var captor = ArgumentCaptor.forClass(AuthorizationRequest.class);
        verify(authorizationService).authorize(captor.capture());
        var request = captor.getValue();
        assertThat(allowed).isTrue();
        assertThat(request.plan().l1().mode()).isEqualTo(AuthorizationPlan.FunctionMode.PERMISSION);
        assertThat(request.plan().l1().permissionCodes()).containsExactly("todo:read");
        assertThat(request.target().objectId()).isEqualTo("42");
    }

    @Test
    @DisplayName("Given 目标 ID 类型与权限 When 方法安全检查 Then 构造 L2 请求并委托唯一授权门面")
    void should_delegate_target_id_overload_to_authorization_service() {
        when(authorizationService.authorize(any())).thenReturn(allowDecision());
        var adapter = new AuthorizationMethodSecurityAdapter(authorizationService);

        var allowed = adapter.hasPermission(authentication, 9L, "todo", "viewer");

        var captor = ArgumentCaptor.forClass(AuthorizationRequest.class);
        verify(authorizationService).authorize(captor.capture());
        var request = captor.getValue();
        assertThat(allowed).isTrue();
        assertThat(request.plan().l1().mode())
                .isEqualTo(AuthorizationPlan.FunctionMode.AUTHENTICATED);
        assertThat(request.plan().l2().combination())
                .isEqualTo(AuthorizationPlan.CombinationMode.ALL_APPLICABLE);
        assertThat(request.plan().l2().requirements())
                .containsExactly(new AuthorizationPlan.RelationRequirement("todo", "9", "viewer"));
        assertThat(request.target()).isEqualTo(new AuthorizationTarget("todo", "viewer", "9"));
    }

    private AuthorizationDecision allowDecision() {
        return new AuthorizationDecision(
                AuthorizationEffect.ALLOW,
                List.of(
                        AuthorizationDecision.LayerDecision.of(
                                AuthorizationLayer.L1_FUNCTION, AuthorizationEffect.ALLOW, "允许")),
                List.of(),
                null,
                null);
    }
}
