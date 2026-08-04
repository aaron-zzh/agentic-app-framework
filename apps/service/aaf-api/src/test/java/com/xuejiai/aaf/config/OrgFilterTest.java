package com.xuejiai.aaf.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationService;
import com.xuejiai.aaf.module.system.org.repository.OrgMemberRepository;
import com.xuejiai.aaf.module.system.org.repository.WorkspaceMemberRepository;
import com.xuejiai.aaf.module.system.org.repository.WorkspaceRepository;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class OrgFilterTest extends BaseMockitoUnitTest {

    @Mock private OperatorContext operatorContext;
    @Mock private AuthorizationService authorizationService;
    @Mock private OrgMemberRepository orgMemberRepository;
    @Mock private WorkspaceRepository workspaceRepository;
    @Mock private WorkspaceMemberRepository workspaceMemberRepository;

    private OrgFilter filter;

    @BeforeEach
    void setUp() {
        OrgContext.clear();
        filter =
                new OrgFilter(
                        operatorContext,
                        authorizationService,
                        orgMemberRepository,
                        workspaceRepository,
                        workspaceMemberRepository);
    }

    @Test
    @DisplayName("Given super_admin GET 且 X-Org-Id=all When 过滤请求 Then 进入全组织上下文并在结束后清理")
    void should_use_all_organizations_for_super_admin_get() throws Exception {
        // 准备参数
        var request = request("GET", "all");
        var response = new MockHttpServletResponse();
        var invoked = new AtomicBoolean();
        when(authorizationService.isCurrentSubjectSuperAdmin()).thenReturn(true);

        // 调用
        filter.doFilter(
                request,
                response,
                (ignoredRequest, ignoredResponse) -> {
                    invoked.set(true);
                    assertThat(OrgContext.isAllOrganizations()).isTrue();
                    assertThat(OrgContext.getCurrentOrgId()).isNull();
                    assertThat(OrgContext.getCurrentWorkspaceId()).isNull();
                });

        // 断言
        assertThat(invoked).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(OrgContext.isAllOrganizations()).isFalse();
        verifyNoInteractions(orgMemberRepository, workspaceRepository, workspaceMemberRepository);
    }

    @Test
    @DisplayName("Given 非 super_admin 且 X-Org-Id=all When 过滤请求 Then 返回 403")
    void should_reject_all_organizations_for_non_super_admin() throws Exception {
        // 准备参数
        var request = request("GET", "all");
        var response = new MockHttpServletResponse();
        var invoked = new AtomicBoolean();
        when(authorizationService.isCurrentSubjectSuperAdmin()).thenReturn(false);

        // 调用
        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> invoked.set(true));

        // 断言
        assertThat(invoked).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(OrgContext.isAllOrganizations()).isFalse();
        verifyNoInteractions(orgMemberRepository, workspaceRepository, workspaceMemberRepository);
    }

    @Test
    @DisplayName("Given X-Org-Id=all 的非 GET 请求 When 过滤请求 Then 在角色检查前拒绝")
    void should_reject_mutation_request_in_all_organizations() throws Exception {
        // 准备参数
        var request = request("POST", "all");
        var response = new MockHttpServletResponse();
        var invoked = new AtomicBoolean();

        // 调用
        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> invoked.set(true));

        // 断言
        assertThat(invoked).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        verify(authorizationService, never()).isCurrentSubjectSuperAdmin();
    }

    @Test
    @DisplayName("Given X-Org-Id=all 同时指定工作区 When 过滤请求 Then 返回 403")
    void should_reject_workspace_in_all_organizations() throws Exception {
        // 准备参数
        var request = request("GET", "all");
        request.addHeader("X-Workspace-Id", "3");
        var response = new MockHttpServletResponse();

        // 调用
        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {});

        // 断言
        assertThat(response.getStatus()).isEqualTo(403);
        verify(authorizationService, never()).isCurrentSubjectSuperAdmin();
    }

    @Test
    @DisplayName("Given 数字组织且用户属于该组织 When 过滤请求 Then 保持单组织上下文行为")
    void should_keep_specific_organization_behavior() throws Exception {
        // 准备参数
        var request = request("GET", "9");
        var response = new MockHttpServletResponse();
        when(operatorContext.currentOwnerId()).thenReturn(Optional.of(7L));
        when(orgMemberRepository.existsByOrgIdAndUserIdAndDeletedFalse(9L, 7L)).thenReturn(true);

        // 调用
        filter.doFilter(
                request,
                response,
                (ignoredRequest, ignoredResponse) -> {
                    assertThat(OrgContext.isAllOrganizations()).isFalse();
                    assertThat(OrgContext.getCurrentOrgId()).isEqualTo(9L);
                });

        // 断言
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(OrgContext.getCurrentOrgId()).isNull();
    }

    @Test
    @DisplayName("Given super_admin 选择非成员数字组织 When 过滤请求 Then 允许进入该组织上下文")
    void should_allow_super_admin_to_select_any_specific_organization() throws Exception {
        // 准备参数
        var request = request("GET", "9");
        var response = new MockHttpServletResponse();
        when(operatorContext.currentOwnerId()).thenReturn(Optional.of(7L));
        when(authorizationService.isCurrentSubjectSuperAdmin()).thenReturn(true);

        // 调用
        filter.doFilter(
                request,
                response,
                (ignoredRequest, ignoredResponse) ->
                        assertThat(OrgContext.getCurrentOrgId()).isEqualTo(9L));

        // 断言
        assertThat(response.getStatus()).isEqualTo(200);
        verify(orgMemberRepository, never()).existsByOrgIdAndUserIdAndDeletedFalse(9L, 7L);
    }

    @Test
    @DisplayName("Given super_admin GET 且工作区为 all When 过滤请求 Then 进入当前组织全工作区上下文")
    void should_use_all_workspaces_for_super_admin_get() throws Exception {
        // 准备参数
        var request = request("GET", "9");
        request.addHeader("X-Workspace-Id", "all");
        var response = new MockHttpServletResponse();
        when(operatorContext.currentOwnerId()).thenReturn(Optional.of(7L));
        when(authorizationService.isCurrentSubjectSuperAdmin()).thenReturn(true);

        // 调用
        filter.doFilter(
                request,
                response,
                (ignoredRequest, ignoredResponse) -> {
                    assertThat(OrgContext.getCurrentOrgId()).isEqualTo(9L);
                    assertThat(OrgContext.getCurrentWorkspaceId()).isNull();
                    assertThat(OrgContext.isAllWorkspaces()).isTrue();
                });

        // 断言
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(OrgContext.isAllWorkspaces()).isFalse();
        verifyNoInteractions(workspaceRepository, workspaceMemberRepository);
    }

    private MockHttpServletRequest request(String method, String orgId) {
        var request = new MockHttpServletRequest();
        request.setMethod(method);
        request.addHeader("X-Org-Id", orgId);
        return request;
    }
}
