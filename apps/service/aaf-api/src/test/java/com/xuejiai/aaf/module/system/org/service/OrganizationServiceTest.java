package com.xuejiai.aaf.module.system.org.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import com.xuejiai.aaf.framework.engine.entitlement.EntitlementChecker;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationService;
import com.xuejiai.aaf.module.system.org.domain.OrgMember;
import com.xuejiai.aaf.module.system.org.domain.Organization;
import com.xuejiai.aaf.module.system.org.repository.OrgMemberRepository;
import com.xuejiai.aaf.module.system.org.repository.OrganizationRepository;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class OrganizationServiceTest extends BaseMockitoUnitTest {

    @Mock private OrganizationRepository organizationRepository;
    @Mock private OrgMemberRepository memberRepository;
    @Mock private EntitlementChecker entitlementChecker;
    @Mock private OperatorContext operatorContext;
    @Mock private AuthorizationService authorizationService;

    private OrganizationService service;

    @BeforeEach
    void setUp() {
        service =
                new OrganizationService(
                        organizationRepository,
                        memberRepository,
                        entitlementChecker,
                        operatorContext,
                        authorizationService);
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

    private Organization organization(Long id, String name) {
        var organization = new Organization();
        organization.setId(id);
        organization.setName(name);
        organization.setSlug("org-" + id);
        organization.setType("team");
        organization.setOwnerId(7L);
        return organization;
    }
}
