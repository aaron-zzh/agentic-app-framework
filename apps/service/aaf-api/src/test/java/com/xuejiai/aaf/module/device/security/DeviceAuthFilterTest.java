package com.xuejiai.aaf.module.device.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import com.xuejiai.aaf.module.device.service.DeviceCredentialService;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;

class DeviceAuthFilterTest extends BaseMockitoUnitTest {

    private static final String DEVICE_API = "/api/device/brief";
    private static final String USER_API = "/api/system/users/me";
    private static final String DEVICE_KEY = "device-r1";
    private static final String CREDENTIAL = "aaf_ed_0123456789ab_secret";

    @Mock private DeviceCredentialService credentialService;

    private DeviceAuthFilter filter;

    @BeforeEach
    void setUp() {
        filter = new DeviceAuthFilter(credentialService);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Given 有效设备凭据 When 访问设备接口 Then 仅授予设备角色")
    void should_only_grant_device_role_when_credential_is_valid() throws Exception {
        // 准备参数
        var principal =
                new DevicePrincipal(1L, DEVICE_KEY, 10L, "test-scenario", 20L, null, 30L, 40L);
        when(credentialService.authenticate(DEVICE_KEY, CREDENTIAL))
                .thenReturn(Optional.of(principal));
        var request = deviceRequest(DEVICE_API, DEVICE_KEY, CREDENTIAL);
        var response = new MockHttpServletResponse();

        // 调用
        filter.doFilter(request, response, protectedEndpoint());

        // 断言
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(authentication.getPrincipal()).isEqualTo(principal);
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_DEVICE");
    }

    @Test
    @DisplayName("Given 凭据已吊销 When 访问设备接口 Then 返回未认证")
    void should_reject_when_credential_is_revoked() throws Exception {
        // mock 方法
        when(credentialService.authenticate(DEVICE_KEY, CREDENTIAL)).thenReturn(Optional.empty());
        var response = new MockHttpServletResponse();

        // 调用
        filter.doFilter(
                deviceRequest(DEVICE_API, DEVICE_KEY, CREDENTIAL), response, protectedEndpoint());

        // 断言
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("Given R1 凭据 When 冒充 R2 访问设备接口 Then 返回未认证")
    void should_reject_when_credential_belongs_to_another_device() throws Exception {
        // 准备参数
        var otherDeviceKey = "device-r2";
        when(credentialService.authenticate(otherDeviceKey, CREDENTIAL))
                .thenReturn(Optional.empty());
        var response = new MockHttpServletResponse();

        // 调用
        filter.doFilter(
                deviceRequest(DEVICE_API, otherDeviceKey, CREDENTIAL),
                response,
                protectedEndpoint());

        // 断言
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        verify(credentialService).authenticate(otherDeviceKey, CREDENTIAL);
    }

    @Test
    @DisplayName("Given 用户 JWT When 访问设备接口 Then 无法取得设备身份")
    void should_reject_user_jwt_when_accessing_device_api() throws Exception {
        // 准备参数
        var request = new MockHttpServletRequest("GET", DEVICE_API);
        request.addHeader("Authorization", "Bearer user-jwt");
        var response = new MockHttpServletResponse();

        // 调用
        filter.doFilter(request, response, protectedEndpoint());

        // 断言
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(credentialService);
    }

    @Test
    @DisplayName("Given 有效设备凭据 When 访问用户接口 Then 无法取得认证身份")
    void should_reject_device_credential_when_accessing_user_api() throws Exception {
        // 准备参数
        var request = deviceRequest(USER_API, DEVICE_KEY, CREDENTIAL);
        var response = new MockHttpServletResponse();

        // 调用
        filter.doFilter(request, response, protectedEndpoint());

        // 断言
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(credentialService);
    }

    private static MockHttpServletRequest deviceRequest(
            String path, String deviceKey, String credential) {
        var request = new MockHttpServletRequest("GET", path);
        request.addHeader(DeviceAuthFilter.DEVICE_KEY_HEADER, deviceKey);
        request.addHeader(DeviceAuthFilter.DEVICE_CREDENTIAL_HEADER, credential);
        return request;
    }

    private static FilterChain protectedEndpoint() {
        return (request, response) -> {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            var hasDeviceRole =
                    authentication != null
                            && authentication.getAuthorities().stream()
                                    .anyMatch(role -> "ROLE_DEVICE".equals(role.getAuthority()));
            ((HttpServletResponse) response)
                    .setStatus(
                            hasDeviceRole
                                    ? HttpServletResponse.SC_OK
                                    : HttpServletResponse.SC_UNAUTHORIZED);
        };
    }
}
