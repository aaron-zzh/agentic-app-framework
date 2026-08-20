package com.xuejiai.aaf.module.system.org.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.context.ApplicationEventPublisher;

import com.xuejiai.aaf.framework.engine.entitlement.EntitlementChecker;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationService;
import com.xuejiai.aaf.module.system.org.domain.OrgMember;
import com.xuejiai.aaf.module.system.org.domain.Organization;
import com.xuejiai.aaf.module.system.org.domain.Workspace;
import com.xuejiai.aaf.module.system.org.domain.WorkspaceMember;
import com.xuejiai.aaf.module.system.org.event.OrganizationCreatedEvent;
import com.xuejiai.aaf.module.system.org.repository.OrgMemberRepository;
import com.xuejiai.aaf.module.system.org.repository.OrganizationRepository;
import com.xuejiai.aaf.module.system.org.repository.WorkspaceMemberRepository;
import com.xuejiai.aaf.module.system.org.repository.WorkspaceRepository;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class OrganizationServiceTest extends BaseMockitoUnitTest {

    @Mock private OrganizationRepository organizationRepository;
    @Mock private OrgMemberRepository memberRepository;
    @Mock private WorkspaceRepository workspaceRepository;
    @Mock private WorkspaceMemberRepository workspaceMemberRepository;
    @Mock private EntitlementChecker entitlementChecker;
    @Mock private OperatorContext operatorContext;
    @Mock private AuthorizationService authorizationService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private OrganizationService service;

    @BeforeEach
    void setUp() {
        service =
                new OrganizationService(
                        organizationRepository,
                        memberRepository,
                        workspaceRepository,
                        workspaceMemberRepository,
                        entitlementChecker,
                        operatorContext,
                        authorizationService,
                        eventPublisher);
    }

    @Test
    @DisplayName("Given 当前主体是 super_admin When 查询可切换组织 Then 返回全部未删除组织")
    void should_return_all_organizations_for_super_admin() {
        // 准备参数
        when(authorizationService.isCurrentSubjectSuperAdmin()).thenReturn(true);
        when(organizationRepository.findAllByDeletedFalse())
                .thenReturn(List.of(organization(1L, "组织一"), organization(2L, "组织二")));

        // 调用
        var result = service.listByUser(7L);

        // 断言
        assertThat(result).extracting("id").containsExactly(1L, 2L);
        verify(memberRepository, never()).findByUserIdAndDeletedFalse(7L);
    }

    @Test
    @DisplayName("Given 当前主体不是 super_admin When 查询可切换组织 Then 仅返回成员组织")
    void should_return_member_organizations_for_regular_user() {
        // 准备参数
        var member = new OrgMember();
        member.setOrgId(2L);
        when(authorizationService.isCurrentSubjectSuperAdmin()).thenReturn(false);
        when(memberRepository.findByUserIdAndDeletedFalse(7L)).thenReturn(List.of(member));
        when(organizationRepository.findByIdInAndDeletedFalse(List.of(2L)))
                .thenReturn(List.of(organization(2L, "组织二")));

        // 调用
        var result = service.listByUser(7L);

        // 断言
        assertThat(result).extracting("id").containsExactly(2L);
        verify(organizationRepository, never()).findAllByDeletedFalse();
    }

    @Test
    @DisplayName("Given 新用户 When 创建个人组织 Then 同步创建默认工作区及成员关系")
    void should_create_default_workspace_when_creating_personal_organization() {
        // mock 方法
        when(organizationRepository.save(any(Organization.class)))
                .thenAnswer(
                        invocation -> {
                            var organization = invocation.<Organization>getArgument(0);
                            organization.setId(10L);
                            return organization;
                        });
        when(workspaceRepository.save(any(Workspace.class)))
                .thenAnswer(
                        invocation -> {
                            var workspace = invocation.<Workspace>getArgument(0);
                            workspace.setId(20L);
                            return workspace;
                        });

        // 调用
        var result = service.createPersonalOrg(7L, "user7");

        // 断言
        assertThat(result.getId()).isEqualTo(10L);
        verify(workspaceRepository)
                .save(
                        argThat(
                                workspace ->
                                        workspace.getOrgId().equals(10L)
                                                && workspace.getOwnerId().equals(7L)
                                                && "default".equals(workspace.getSlug())));
        verify(workspaceMemberRepository)
                .save(
                        argThat(
                                member ->
                                        member.getOrgId().equals(10L)
                                                && member.getWorkspaceId().equals(20L)
                                                && member.getUserId().equals(7L)));
        verify(eventPublisher)
                .publishEvent(
                        argThat(
                                (Object event) ->
                                        event instanceof OrganizationCreatedEvent created
                                                && created.organizationId().equals(10L)));
    }

    @Test
    @DisplayName("Given 普通组织成员 When 移出组织 Then 同步撤销其工作区成员关系")
    void should_remove_workspace_memberships_when_removing_organization_member() {
        // 准备参数
        var member = orgMember(1L, 10L, 20L, "member");
        var workspaceMember = new WorkspaceMember();
        workspaceMember.setId(30L);
        workspaceMember.setOrgId(10L);
        workspaceMember.setWorkspaceId(40L);
        workspaceMember.setUserId(20L);

        // mock 方法
        when(authorizationService.isCurrentSubjectSuperAdmin()).thenReturn(true);
        when(memberRepository.findByOrgIdAndUserIdAndDeletedFalse(10L, 20L))
                .thenReturn(Optional.of(member));
        when(workspaceRepository.existsByOrgIdAndOwnerIdAndDeletedFalse(10L, 20L))
                .thenReturn(false);
        when(workspaceMemberRepository.findByOrgIdAndUserIdAndDeletedFalse(10L, 20L))
                .thenReturn(List.of(workspaceMember));

        // 调用
        service.removeMember(10L, 20L);

        // 断言
        verify(workspaceMemberRepository).delete(workspaceMember);
        verify(memberRepository).deleteById(1L);
    }

    @Test
    @DisplayName("Given 成员仍管理工作区 When 移出组织 Then 拒绝操作")
    void should_reject_removal_when_member_owns_workspace() {
        // 准备参数
        var member = orgMember(1L, 10L, 20L, "member");

        // mock 方法
        when(authorizationService.isCurrentSubjectSuperAdmin()).thenReturn(true);
        when(memberRepository.findByOrgIdAndUserIdAndDeletedFalse(10L, 20L))
                .thenReturn(Optional.of(member));
        when(workspaceRepository.existsByOrgIdAndOwnerIdAndDeletedFalse(10L, 20L)).thenReturn(true);

        // 调用 + 断言
        assertThatThrownBy(() -> service.removeMember(10L, 20L)).hasMessageContaining("工作区管理者");
        verify(memberRepository, never()).deleteById(1L);
    }

    private Organization organization(Long id, String name) {
        var organization = new Organization();
        organization.setId(id);
        organization.setName(name);
        organization.setSlug("org-" + id);
        organization.setType("team");
        organization.setOwnerId(7L);
        return organization;
    }

    private OrgMember orgMember(Long id, Long orgId, Long userId, String role) {
        var member = new OrgMember();
        member.setId(id);
        member.setOrgId(orgId);
        member.setUserId(userId);
        member.setRole(role);
        return member;
    }
}
