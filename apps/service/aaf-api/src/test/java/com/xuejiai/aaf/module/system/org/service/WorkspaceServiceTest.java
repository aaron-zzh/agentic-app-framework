package com.xuejiai.aaf.module.system.org.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationService;
import com.xuejiai.aaf.module.system.org.domain.OrgMember;
import com.xuejiai.aaf.module.system.org.domain.Workspace;
import com.xuejiai.aaf.module.system.org.repository.OrgMemberRepository;
import com.xuejiai.aaf.module.system.org.repository.WorkspaceMemberRepository;
import com.xuejiai.aaf.module.system.org.repository.WorkspaceRepository;
import com.xuejiai.aaf.module.system.org.vo.WorkspacePageDTO;
import com.xuejiai.aaf.module.system.org.vo.WorkspaceUpdateDTO;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

class WorkspaceServiceTest extends BaseMockitoUnitTest {

    @Mock private WorkspaceRepository workspaceRepository;
    @Mock private WorkspaceMemberRepository workspaceMemberRepository;
    @Mock private OrgMemberRepository orgMemberRepository;
    @Mock private OperatorContext operatorContext;
    @Mock private AuthorizationService authorizationService;

    private WorkspaceService service;

    @BeforeEach
    void setUp() {
        OrgContext.clear();
        service =
                new WorkspaceService(
                        workspaceRepository,
                        workspaceMemberRepository,
                        orgMemberRepository,
                        operatorContext,
                        authorizationService);
    }

    @AfterEach
    void tearDown() {
        OrgContext.clear();
    }

    @Test
    @DisplayName("Given 普通组织成员 When 构建工作区列表条件 Then 一次查询实际成员工作区 ID")
    void should_filter_member_workspaces_with_single_projection_query() {
        // 准备参数
        OrgContext.setCurrentOrgId(9L);
        when(operatorContext.currentOwnerId()).thenReturn(Optional.of(7L));
        when(orgMemberRepository.findByOrgIdAndUserIdAndDeletedFalse(9L, 7L))
                .thenReturn(Optional.of(member(9L, 7L, "member")));
        when(workspaceMemberRepository.findWorkspaceIdsByOrgIdAndUserId(9L, 7L))
                .thenReturn(List.of(101L, 102L));
        var root = mock(Root.class);
        var query = mock(CriteriaQuery.class);
        var cb = mock(CriteriaBuilder.class);
        var idPath = mock(Path.class);
        var predicate = mock(Predicate.class);
        when(root.<Long>get("id")).thenReturn(idPath);
        when(idPath.in(List.of(101L, 102L))).thenReturn(predicate);

        // 调用
        var actual = service.buildSpec(new WorkspacePageDTO()).toPredicate(root, query, cb);

        // 断言
        assertThat(actual).isSameAs(predicate);
        verify(workspaceMemberRepository).findWorkspaceIdsByOrgIdAndUserId(9L, 7L);
        verify(workspaceMemberRepository, never()).findByOrgIdAndUserIdAndDeletedFalse(9L, 7L);
    }

    @Test
    @DisplayName("Given 普通成员没有加入工作区 When 构建工作区列表条件 Then 返回恒假条件")
    void should_return_false_spec_when_member_has_no_workspace() {
        // 准备参数
        OrgContext.setCurrentOrgId(9L);
        when(operatorContext.currentOwnerId()).thenReturn(Optional.of(7L));
        when(orgMemberRepository.findByOrgIdAndUserIdAndDeletedFalse(9L, 7L))
                .thenReturn(Optional.of(member(9L, 7L, "member")));
        when(workspaceMemberRepository.findWorkspaceIdsByOrgIdAndUserId(9L, 7L))
                .thenReturn(List.of());
        var root = mock(Root.class);
        var query = mock(CriteriaQuery.class);
        var cb = mock(CriteriaBuilder.class);
        var predicate = mock(Predicate.class);
        when(cb.disjunction()).thenReturn(predicate);

        // 调用
        var actual = service.buildSpec(new WorkspacePageDTO()).toPredicate(root, query, cb);

        // 断言
        assertThat(actual).isSameAs(predicate);
        verify(root, never()).get("id");
    }

    @Test
    @DisplayName("Given 受限全组织上下文 When 构建工作区列表条件 Then 仅使用上下文中的可访问工作区 ID")
    void should_filter_by_accessible_workspace_ids_in_all_organizations() {
        // 准备参数
        OrgContext.useAllOrganizations(List.of(9L, 10L), List.of(101L, 201L));
        var root = mock(Root.class);
        var query = mock(CriteriaQuery.class);
        var cb = mock(CriteriaBuilder.class);
        var idPath = mock(Path.class);
        var predicate = mock(Predicate.class);
        when(root.<Long>get("id")).thenReturn(idPath);
        when(idPath.in(OrgContext.getAccessibleWorkspaceIds())).thenReturn(predicate);

        // 调用
        var actual = service.buildSpec(new WorkspacePageDTO()).toPredicate(root, query, cb);

        // 断言
        assertThat(actual).isSameAs(predicate);
        verifyNoInteractions(operatorContext, orgMemberRepository, workspaceMemberRepository);
    }

    @Test
    @DisplayName("Given 组织管理者请求 memberOnly When 构建条件 Then 仍仅查询实际加入的工作区 ID")
    void should_filter_actual_memberships_for_manager_member_only_request() {
        // 准备参数
        OrgContext.setCurrentOrgId(9L);
        when(operatorContext.currentOwnerId()).thenReturn(Optional.of(7L));
        when(workspaceMemberRepository.findWorkspaceIdsByOrgIdAndUserId(9L, 7L))
                .thenReturn(List.of(101L));
        var request = new WorkspacePageDTO();
        request.setMemberOnly(true);
        var root = mock(Root.class);
        var query = mock(CriteriaQuery.class);
        var cb = mock(CriteriaBuilder.class);
        var idPath = mock(Path.class);
        var predicate = mock(Predicate.class);
        when(root.<Long>get("id")).thenReturn(idPath);
        when(idPath.in(List.of(101L))).thenReturn(predicate);

        // 调用
        var actual = service.buildSpec(request).toPredicate(root, query, cb);

        // 断言
        assertThat(actual).isSameAs(predicate);
        verify(workspaceMemberRepository).findWorkspaceIdsByOrgIdAndUserId(9L, 7L);
        verifyNoInteractions(orgMemberRepository, authorizationService);
    }

    @Test
    @DisplayName("Given 全组织 memberOnly When 构建条件 Then 批量查询当前用户实际加入的工作区 ID")
    void should_filter_actual_memberships_across_organizations_for_member_only_request() {
        // 准备参数
        OrgContext.useAllOrganizations();
        when(operatorContext.currentOwnerId()).thenReturn(Optional.of(7L));
        when(workspaceMemberRepository.findWorkspaceIdsByUserId(7L))
                .thenReturn(List.of(101L, 201L));
        var request = new WorkspacePageDTO();
        request.setMemberOnly(true);
        var root = mock(Root.class);
        var query = mock(CriteriaQuery.class);
        var cb = mock(CriteriaBuilder.class);
        var idPath = mock(Path.class);
        var predicate = mock(Predicate.class);
        when(root.<Long>get("id")).thenReturn(idPath);
        when(idPath.in(List.of(101L, 201L))).thenReturn(predicate);

        // 调用
        var actual = service.buildSpec(request).toPredicate(root, query, cb);

        // 断言
        assertThat(actual).isSameAs(predicate);
        verify(workspaceMemberRepository).findWorkspaceIdsByUserId(7L);
        verifyNoInteractions(orgMemberRepository, authorizationService);
    }

    @Test
    @DisplayName("Given 组织所有者 When 构建工作区列表条件 Then 不逐工作区查询成员关系")
    void should_keep_all_org_workspaces_for_owner_without_member_queries() {
        // 准备参数
        OrgContext.setCurrentOrgId(9L);
        when(operatorContext.currentOwnerId()).thenReturn(Optional.of(7L));
        when(orgMemberRepository.findByOrgIdAndUserIdAndDeletedFalse(9L, 7L))
                .thenReturn(Optional.of(member(9L, 7L, "owner")));

        // 调用
        var actual =
                service.buildSpec(new WorkspacePageDTO())
                        .toPredicate(
                                mock(Root.class),
                                mock(CriteriaQuery.class),
                                mock(CriteriaBuilder.class));

        // 断言
        assertThat(actual).isNull();
        verifyNoInteractions(workspaceMemberRepository);
    }

    @Test
    @DisplayName("Given super_admin When 构建工作区列表条件 Then 不查询组织或工作区成员关系")
    void should_keep_unrestricted_workspace_list_for_super_admin() {
        // 准备参数
        when(authorizationService.isCurrentSubjectSuperAdmin()).thenReturn(true);

        // 调用
        var actual =
                service.buildSpec(new WorkspacePageDTO())
                        .toPredicate(
                                mock(Root.class),
                                mock(CriteriaQuery.class),
                                mock(CriteriaBuilder.class));

        // 断言
        assertThat(actual).isNull();
        verifyNoInteractions(operatorContext, orgMemberRepository, workspaceMemberRepository);
    }

    @Test
    @DisplayName("Given 当前用户是工作区 owner When 改名校验 Then 允许更新")
    void should_allow_workspace_owner_to_rename() {
        // 准备参数
        var workspace = workspace(101L, 9L, 7L);
        when(operatorContext.currentOwnerId()).thenReturn(Optional.of(7L));

        // 调用 + 断言
        assertThatCode(() -> service.beforeUpdate(workspace, new WorkspaceUpdateDTO("新名称")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Given super_admin 不是工作区 owner When 改名校验 Then 拒绝更新")
    void should_reject_non_owner_super_admin_rename() {
        // 准备参数
        var workspace = workspace(101L, 9L, 8L);
        when(operatorContext.currentOwnerId()).thenReturn(Optional.of(7L));

        // 调用 + 断言
        assertThatThrownBy(() -> service.beforeUpdate(workspace, new WorkspaceUpdateDTO("新名称")))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(1_007_012);
        verifyNoInteractions(authorizationService);
    }

    private OrgMember member(Long orgId, Long userId, String role) {
        var member = new OrgMember();
        member.setOrgId(orgId);
        member.setUserId(userId);
        member.setRole(role);
        return member;
    }

    private Workspace workspace(Long id, Long orgId, Long ownerId) {
        var workspace = new Workspace();
        workspace.setId(id);
        workspace.setOrgId(orgId);
        workspace.setOwnerId(ownerId);
        return workspace;
    }
}
