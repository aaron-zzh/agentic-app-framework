package com.xuejiai.aaf.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
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
import com.xuejiai.aaf.module.system.org.domain.OrgMember;
import com.xuejiai.aaf.module.system.org.domain.Workspace;
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
        when(operatorContext.currentOwnerId()).thenReturn(Optional.of(7L));
        when(authorizationService.isCurrentSubjectSuperAdmin()).thenReturn(true);

        // 调用
        filter.doFilter(
                request,
                response,
                (ignoredRequest, ignoredResponse) -> {
                    invoked.set(true);
                    assertThat(OrgContext.isAllOrganizations()).isTrue();
                    assertThat(OrgContext.isAllOrganizationsUnrestricted()).isTrue();
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
    @DisplayName("Given 普通用户仅加入一个组织 When 选择全部组织 Then 返回 403")
    void should_reject_all_organizations_for_single_org_member() throws Exception {
        // 准备参数
        var request = request("GET", "all");
        var response = new MockHttpServletResponse();
        var invoked = new AtomicBoolean();
        when(operatorContext.currentOwnerId()).thenReturn(Optional.of(7L));
        when(orgMemberRepository.findByUserIdAndDeletedFalse(7L))
                .thenReturn(List.of(member(9L, 7L, "member")));

        // 调用
        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> invoked.set(true));

        // 断言
        assertThat(invoked).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(OrgContext.isAllOrganizations()).isFalse();
        verifyNoInteractions(workspaceRepository, workspaceMemberRepository);
    }

    @Test
    @DisplayName("Given 普通用户加入两个组织 When 选择全部组织 Then 批量解析实际可访问工作区")
    void should_resolve_accessible_workspaces_for_multi_org_member() throws Exception {
        // 准备参数
        var request = request("GET", "all");
        var response = new MockHttpServletResponse();
        when(operatorContext.currentOwnerId()).thenReturn(Optional.of(7L));
        when(orgMemberRepository.findByUserIdAndDeletedFalse(7L))
                .thenReturn(List.of(member(9L, 7L, "member"), member(10L, 7L, "admin")));
        when(workspaceMemberRepository.findWorkspaceIdsByUserId(7L)).thenReturn(List.of(101L));
        when(workspaceRepository.findByOrgIdInAndDeletedFalse(List.of(10L)))
                .thenReturn(List.of(workspace(201L, 10L)));

        // 调用
        filter.doFilter(
                request,
                response,
                (ignoredRequest, ignoredResponse) -> {
                    assertThat(OrgContext.isAllOrganizations()).isTrue();
                    assertThat(OrgContext.isAllOrganizationsUnrestricted()).isFalse();
                    assertThat(OrgContext.getAccessibleOrgIds()).containsExactlyInAnyOrder(9L, 10L);
                    assertThat(OrgContext.getAccessibleWorkspaceIds())
                            .containsExactlyInAnyOrder(101L, 201L);
                });

        // 断言
        assertThat(response.getStatus()).isEqualTo(200);
        verify(workspaceMemberRepository).findWorkspaceIdsByUserId(7L);
        verify(workspaceRepository).findByOrgIdInAndDeletedFalse(List.of(10L));
        verify(workspaceMemberRepository, never()).findByUserIdAndDeletedFalse(7L);
    }

    @Test
    @DisplayName("Given X-Org-Id=all 的写请求 When 过滤请求 Then 在角色检查前拒绝")
    void should_reject_mutation_request_in_all_organizations() throws Exception {
        // 准备参数
        var request = request("PUT", "all");
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
    @DisplayName("Given X-Org-Id=all 的精确登记查询 POST When 过滤请求 Then 允许读取")
    void should_allow_registered_read_only_post_in_all_organizations() throws Exception {
        // 准备参数
        var request = request("POST", "all");
        request.setRequestURI("/api/system/dashboards/widgets/22/data");
        var response = new MockHttpServletResponse();
        var invoked = new AtomicBoolean();
        when(operatorContext.currentOwnerId()).thenReturn(Optional.of(7L));
        when(authorizationService.isCurrentSubjectSuperAdmin()).thenReturn(true);

        // 调用
        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> invoked.set(true));

        // 断言
        assertThat(invoked).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Given X-Org-Id=all 的未登记 POST When 过滤请求 Then 返回 403")
    void should_reject_unregistered_post_in_all_organizations() throws Exception {
        // 准备参数
        var request = request("POST", "all");
        request.setRequestURI("/api/system/workspaces/_query");
        var response = new MockHttpServletResponse();

        // 调用
        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {});

        // 断言
        assertThat(response.getStatus()).isEqualTo(403);
        verifyNoInteractions(operatorContext, authorizationService);
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
                (ignoredRequest, ignoredResponse) ->
                        assertThat(OrgContext.getCurrentOrgId()).isEqualTo(9L));

        // 断言
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(OrgContext.getCurrentOrgId()).isNull();
    }

    @Test
    @DisplayName("Given super_admin GET 非成员数字组织 When 过滤请求 Then 仅允许只读进入组织上下文")
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
        verify(orgMemberRepository).existsByOrgIdAndUserIdAndDeletedFalse(9L, 7L);
    }

    @Test
    @DisplayName("Given super_admin PUT 非成员数字组织 When 过滤请求 Then 返回 403")
    void should_reject_super_admin_mutating_unjoined_organization() throws Exception {
        // 准备参数
        var request = request("PUT", "9");
        var response = new MockHttpServletResponse();
        var invoked = new AtomicBoolean();
        when(operatorContext.currentOwnerId()).thenReturn(Optional.of(7L));
        when(authorizationService.isCurrentSubjectSuperAdmin()).thenReturn(true);

        // 调用
        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> invoked.set(true));

        // 断言
        assertThat(invoked).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        verify(orgMemberRepository).existsByOrgIdAndUserIdAndDeletedFalse(9L, 7L);
        verifyNoInteractions(workspaceRepository, workspaceMemberRepository);
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

    @Test
    @DisplayName("Given 工作区为 all 的写请求 When 过滤请求 Then 保持聚合写拒绝")
    void should_reject_mutation_in_all_workspaces() throws Exception {
        // 准备参数
        var request = request("PUT", "9");
        request.addHeader("X-Workspace-Id", "all");
        var response = new MockHttpServletResponse();
        when(operatorContext.currentOwnerId()).thenReturn(Optional.of(7L));
        when(authorizationService.isCurrentSubjectSuperAdmin()).thenReturn(true);

        // 调用
        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {});

        // 断言
        assertThat(response.getStatus()).isEqualTo(403);
        verifyNoInteractions(workspaceRepository, workspaceMemberRepository);
    }

    @Test
    @DisplayName("Given super_admin 未加入具体工作区 When GET 请求 Then 允许只读访问")
    void should_allow_super_admin_reading_unjoined_workspace() throws Exception {
        // 准备参数
        var request = request("GET", "9");
        request.addHeader("X-Workspace-Id", "101");
        var response = new MockHttpServletResponse();
        var invoked = new AtomicBoolean();
        when(operatorContext.currentOwnerId()).thenReturn(Optional.of(7L));
        when(authorizationService.isCurrentSubjectSuperAdmin()).thenReturn(true);
        when(workspaceRepository.findById(101L)).thenReturn(Optional.of(workspace(101L, 9L)));

        // 调用
        filter.doFilter(
                request,
                response,
                (ignoredRequest, ignoredResponse) -> {
                    invoked.set(true);
                    assertThat(OrgContext.getCurrentWorkspaceId()).isEqualTo(101L);
                });

        // 断言
        assertThat(invoked).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
        verify(workspaceMemberRepository).existsByWorkspaceIdAndUserIdAndDeletedFalse(101L, 7L);
    }

    @Test
    @DisplayName("Given super_admin 未加入具体工作区 When PUT 请求 Then 返回 403")
    void should_reject_super_admin_mutating_unjoined_workspace() throws Exception {
        // 准备参数
        var request = request("PUT", "9");
        request.addHeader("X-Workspace-Id", "101");
        var response = new MockHttpServletResponse();
        var invoked = new AtomicBoolean();
        when(operatorContext.currentOwnerId()).thenReturn(Optional.of(7L));
        when(orgMemberRepository.existsByOrgIdAndUserIdAndDeletedFalse(9L, 7L)).thenReturn(true);
        when(authorizationService.isCurrentSubjectSuperAdmin()).thenReturn(true);
        when(workspaceRepository.findById(101L)).thenReturn(Optional.of(workspace(101L, 9L)));

        // 调用
        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> invoked.set(true));

        // 断言
        assertThat(invoked).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("Given 普通成员已加入具体工作区 When PUT 请求 Then 允许进入后续 owner 校验")
    void should_allow_workspace_member_mutation_to_reach_owner_check() throws Exception {
        // 准备参数
        var request = request("PUT", "9");
        request.addHeader("X-Workspace-Id", "101");
        var response = new MockHttpServletResponse();
        var invoked = new AtomicBoolean();
        when(operatorContext.currentOwnerId()).thenReturn(Optional.of(7L));
        when(orgMemberRepository.existsByOrgIdAndUserIdAndDeletedFalse(9L, 7L)).thenReturn(true);
        when(workspaceRepository.findById(101L)).thenReturn(Optional.of(workspace(101L, 9L)));
        when(workspaceMemberRepository.existsByWorkspaceIdAndUserIdAndDeletedFalse(101L, 7L))
                .thenReturn(true);

        // 调用
        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> invoked.set(true));

        // 断言
        assertThat(invoked).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Given super_admin 拼接其他组织的工作区 When GET 请求 Then 返回 403")
    void should_reject_workspace_from_another_org_for_super_admin() throws Exception {
        // 准备参数
        var request = request("GET", "9");
        request.addHeader("X-Workspace-Id", "101");
        var response = new MockHttpServletResponse();
        when(operatorContext.currentOwnerId()).thenReturn(Optional.of(7L));
        when(authorizationService.isCurrentSubjectSuperAdmin()).thenReturn(true);
        when(workspaceRepository.findById(101L)).thenReturn(Optional.of(workspace(101L, 10L)));

        // 调用
        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {});

        // 断言
        assertThat(response.getStatus()).isEqualTo(403);
        verify(workspaceMemberRepository, never())
                .existsByWorkspaceIdAndUserIdAndDeletedFalse(101L, 7L);
    }

    private MockHttpServletRequest request(String method, String orgId) {
        var request = new MockHttpServletRequest();
        request.setMethod(method);
        request.addHeader("X-Org-Id", orgId);
        return request;
    }

    private OrgMember member(Long orgId, Long userId, String role) {
        var member = new OrgMember();
        member.setOrgId(orgId);
        member.setUserId(userId);
        member.setRole(role);
        return member;
    }

    private Workspace workspace(Long id, Long orgId) {
        var workspace = new Workspace();
        workspace.setId(id);
        workspace.setOrgId(orgId);
        return workspace;
    }
}
