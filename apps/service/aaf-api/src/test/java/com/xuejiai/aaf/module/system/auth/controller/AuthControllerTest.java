package com.xuejiai.aaf.module.system.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.core.env.Environment;

import com.xuejiai.aaf.module.system.auth.captcha.EsaCaptchaVerifier;
import com.xuejiai.aaf.module.system.auth.service.AuthService;
import com.xuejiai.aaf.module.system.permission.service.PermissionSecurityService;
import com.xuejiai.aaf.module.system.role.repository.RoleRepository;
import com.xuejiai.aaf.module.system.role.repository.UserRoleRepository;
import com.xuejiai.aaf.module.system.user.service.UserService;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

class AuthControllerTest extends BaseMockitoUnitTest {

    @Mock private AuthService authService;
    @Mock private UserService userService;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PermissionSecurityService permissionSecurityService;
    @Mock private Environment environment;
    @Mock private EsaCaptchaVerifier esaCaptchaVerifier;
    @InjectMocks private AuthController controller;

    @Test
    @DisplayName("Given 当前主体正式权限码 When 查询 me Then 返回完整 authorityCodes 与同源 capabilities")
    void should_return_authority_codes_and_capabilities_from_same_formal_source() {
        // 准备参数
        var request = mock(HttpServletRequest.class);
        var response = mock(HttpServletResponse.class);
        var authorities = Set.of("aigc:project:action", "aigc:work:publish");
        when(authService.currentUserId()).thenReturn(7L);
        when(userRoleRepository.findByUserIdAndDeletedFalse(7L)).thenReturn(List.of());
        when(permissionSecurityService.authorityCodes(7L)).thenReturn(authorities);

        // 调用
        var me = controller.me(request, response).data();

        // 断言
        assertThat(me.authorityCodes()).containsExactlyInAnyOrderElementsOf(authorities);
        assertThat(me.capabilities())
                .containsEntry("aigc:project:action", true)
                .containsEntry("aigc:work:publish", true)
                .hasSize(authorities.size());
    }
}
