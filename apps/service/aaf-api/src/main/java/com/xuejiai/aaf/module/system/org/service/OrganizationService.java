package com.xuejiai.aaf.module.system.org.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;
import static com.xuejiai.aaf.module.system.ErrorCodeConstants.ORG_MANAGER_REQUIRED;
import static com.xuejiai.aaf.module.system.ErrorCodeConstants.ORG_MEMBER_ALREADY_EXISTS;
import static com.xuejiai.aaf.module.system.ErrorCodeConstants.ORG_MEMBER_NOT_FOUND;
import static com.xuejiai.aaf.module.system.ErrorCodeConstants.ORG_MEMBER_REQUIRED;
import static com.xuejiai.aaf.module.system.ErrorCodeConstants.ORG_MEMBER_WORKSPACE_OWNER_REMOVE_FORBIDDEN;
import static com.xuejiai.aaf.module.system.ErrorCodeConstants.ORG_NOT_FOUND;
import static com.xuejiai.aaf.module.system.ErrorCodeConstants.ORG_OWNER_REMOVE_FORBIDDEN;
import static com.xuejiai.aaf.module.system.ErrorCodeConstants.ORG_OWNER_ROLE_CHANGE_FORBIDDEN;
import static com.xuejiai.aaf.module.system.ErrorCodeConstants.ORG_PERSONAL_DELETE_FORBIDDEN;
import static com.xuejiai.aaf.module.system.ErrorCodeConstants.ORG_SLUG_EXISTS;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.engine.entitlement.EntitlementChecker;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationService;
import com.xuejiai.aaf.module.system.org.domain.OrgMember;
import com.xuejiai.aaf.module.system.org.domain.Organization;
import com.xuejiai.aaf.module.system.org.domain.Workspace;
import com.xuejiai.aaf.module.system.org.domain.WorkspaceMember;
import com.xuejiai.aaf.module.system.org.repository.OrgMemberRepository;
import com.xuejiai.aaf.module.system.org.repository.OrganizationRepository;
import com.xuejiai.aaf.module.system.org.repository.WorkspaceMemberRepository;
import com.xuejiai.aaf.module.system.org.repository.WorkspaceRepository;
import com.xuejiai.aaf.module.system.org.vo.OrgMemberAddDTO;
import com.xuejiai.aaf.module.system.org.vo.OrgMemberRoleUpdateDTO;
import com.xuejiai.aaf.module.system.org.vo.OrgMemberVO;
import com.xuejiai.aaf.module.system.org.vo.OrganizationCreateDTO;
import com.xuejiai.aaf.module.system.org.vo.OrganizationUpdateDTO;
import com.xuejiai.aaf.module.system.org.vo.OrganizationVO;

import lombok.RequiredArgsConstructor;

/**
 * 组织管理服务。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrganizationService {

    private static final String DEFAULT_WORKSPACE_NAME = "默认工作区";
    private static final String DEFAULT_WORKSPACE_SLUG = "default";

    private final OrganizationRepository orgRepository;
    private final OrgMemberRepository memberRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final EntitlementChecker entitlementChecker;
    private final OperatorContext operatorContext;
    private final AuthorizationService authorizationService;

    /** 获取用户可切换的组织；super_admin 可切换全部组织，其他用户仅返回成员组织。 */
    public List<OrganizationVO> listByUser(Long userId) {
        if (authorizationService.isCurrentSubjectSuperAdmin()) {
            return orgRepository.findAllByDeletedFalse().stream()
                    .map(org -> toVO(org, null))
                    .toList();
        }
        var memberships = memberRepository.findByUserIdAndDeletedFalse(userId);
        Map<Long, OrgMember> membershipByOrg =
                memberships.stream()
                        .collect(
                                Collectors.toMap(
                                        OrgMember::getOrgId,
                                        Function.identity(),
                                        (first, ignored) -> first));
        return orgRepository
                .findByIdInAndDeletedFalse(membershipByOrg.keySet().stream().toList())
                .stream()
                .map(org -> toVO(org, membershipByOrg.get(org.getId()).getRole()))
                .toList();
    }

    public OrganizationVO getById(Long id) {
        var org = findOrg(id);
        var membership = requireMember(id);
        return toVO(org, membership == null ? null : membership.getRole());
    }

    /** 创建团队组织 */
    @Transactional
    public OrganizationVO create(OrganizationCreateDTO dto, Long currentUserId) {
        if (orgRepository.existsBySlugAndDeletedFalse(dto.slug())) {
            throw exception(ORG_SLUG_EXISTS);
        }
        var org = new Organization();
        org.setName(dto.name());
        org.setSlug(dto.slug());
        org.setType("team");
        org.setOwnerId(currentUserId);
        org = orgRepository.save(org);

        // 创建者自动成为 owner
        var member = new OrgMember();
        member.setOrgId(org.getId());
        member.setUserId(currentUserId);
        member.setRole("owner");
        memberRepository.save(member);

        return toVO(org, "owner");
    }

    @Transactional
    public OrganizationVO update(Long id, OrganizationUpdateDTO dto) {
        var org = findOrg(id);
        var membership = requireManager(id);
        if (dto.name() != null) {
            org.setName(dto.name());
        }
        return toVO(orgRepository.save(org), membership == null ? null : membership.getRole());
    }

    @Transactional
    public void delete(Long id) {
        var org = findOrg(id);
        requireManager(id);
        if (org.isPersonal()) {
            throw exception(ORG_PERSONAL_DELETE_FORBIDDEN);
        }
        orgRepository.deleteById(id);
    }

    /** 为新用户创建个人组织及其默认工作区。 */
    @Transactional
    public Organization createPersonalOrg(Long userId, String username) {
        var org = new Organization();
        org.setName(username);
        org.setSlug("personal-" + userId);
        org.setType("personal");
        org.setOwnerId(userId);
        org = orgRepository.save(org);

        var member = new OrgMember();
        member.setOrgId(org.getId());
        member.setUserId(userId);
        member.setRole("owner");
        memberRepository.save(member);

        var workspace = new Workspace();
        workspace.setOrgId(org.getId());
        workspace.setName(DEFAULT_WORKSPACE_NAME);
        workspace.setSlug(DEFAULT_WORKSPACE_SLUG);
        workspace.setOwnerId(userId);
        workspace = workspaceRepository.save(workspace);

        var workspaceMember = new WorkspaceMember();
        workspaceMember.setOrgId(org.getId());
        workspaceMember.setWorkspaceId(workspace.getId());
        workspaceMember.setUserId(userId);
        workspaceMember.setOwnerId(userId);
        workspaceMemberRepository.save(workspaceMember);

        return org;
    }

    // ==================== 成员管理 ====================

    public List<OrgMemberVO> listMembers(Long orgId) {
        findOrg(orgId);
        requireMember(orgId);
        return memberRepository.findByOrgIdAndDeletedFalse(orgId).stream()
                .map(this::toMemberVO)
                .toList();
    }

    @Transactional
    public OrgMemberVO addMember(Long orgId, OrgMemberAddDTO dto) {
        findOrg(orgId); // 确认组织存在
        requireManager(orgId);
        if (memberRepository.existsByOrgIdAndUserIdAndDeletedFalse(orgId, dto.userId())) {
            throw exception(ORG_MEMBER_ALREADY_EXISTS);
        }
        operatorContext
                .currentOwnerId()
                .ifPresent(uid -> entitlementChecker.checkAndConsume(uid, "member_count", 1));
        var member = new OrgMember();
        member.setOrgId(orgId);
        member.setUserId(dto.userId());
        member.setRole(dto.role());
        return toMemberVO(memberRepository.save(member));
    }

    /**
     * 修改成员角色
     *
     * @param orgId 组织 ID
     * @param memberId 成员记录 ID
     * @param dto 角色更新请求
     * @return 更新后的成员信息
     */
    @Transactional
    public OrgMemberVO updateMemberRole(Long orgId, Long memberId, OrgMemberRoleUpdateDTO dto) {
        requireManager(orgId);
        var member =
                memberRepository
                        .findById(memberId)
                        .orElseThrow(() -> exception(ORG_MEMBER_NOT_FOUND));
        if (!member.getOrgId().equals(orgId)) {
            throw exception(ORG_MEMBER_NOT_FOUND);
        }
        if ("owner".equals(member.getRole())) {
            throw exception(ORG_OWNER_ROLE_CHANGE_FORBIDDEN);
        }
        member.setRole(dto.role());
        return toMemberVO(memberRepository.save(member));
    }

    @Transactional
    public void removeMember(Long orgId, Long userId) {
        requireManager(orgId);
        var member =
                memberRepository
                        .findByOrgIdAndUserIdAndDeletedFalse(orgId, userId)
                        .orElseThrow(() -> exception(ORG_MEMBER_NOT_FOUND));
        if ("owner".equals(member.getRole())) {
            throw exception(ORG_OWNER_REMOVE_FORBIDDEN);
        }
        if (workspaceRepository.existsByOrgIdAndOwnerIdAndDeletedFalse(orgId, userId)) {
            throw exception(ORG_MEMBER_WORKSPACE_OWNER_REMOVE_FORBIDDEN);
        }
        workspaceMemberRepository
                .findByOrgIdAndUserIdAndDeletedFalse(orgId, userId)
                .forEach(workspaceMemberRepository::delete);
        memberRepository.deleteById(member.getId());
        operatorContext
                .currentOwnerId()
                .ifPresent(uid -> entitlementChecker.consume(uid, "member_count", -1));
    }

    // ==================== 私有方法 ====================

    /** super_admin 返回 null；普通用户必须是组织成员。 */
    private OrgMember requireMember(Long orgId) {
        if (authorizationService.isCurrentSubjectSuperAdmin()) {
            return null;
        }
        var userId =
                operatorContext.currentOwnerId().orElseThrow(() -> exception(ORG_MEMBER_REQUIRED));
        return memberRepository
                .findByOrgIdAndUserIdAndDeletedFalse(orgId, userId)
                .orElseThrow(() -> exception(ORG_MEMBER_REQUIRED));
    }

    /** super_admin 返回 null；普通用户必须是组织 owner/admin。 */
    private OrgMember requireManager(Long orgId) {
        var membership = requireMember(orgId);
        if (membership == null) {
            return null;
        }
        if (!"owner".equals(membership.getRole()) && !"admin".equals(membership.getRole())) {
            throw exception(ORG_MANAGER_REQUIRED);
        }
        return membership;
    }

    private Organization findOrg(Long id) {
        return orgRepository.findById(id).orElseThrow(() -> exception(ORG_NOT_FOUND));
    }

    private OrganizationVO toVO(Organization org, String memberRole) {
        return new OrganizationVO(
                org.getId(),
                org.getName(),
                org.getSlug(),
                org.getType(),
                org.getOwnerId(),
                memberRole,
                org.getCreateTime());
    }

    private OrgMemberVO toMemberVO(OrgMember m) {
        return new OrgMemberVO(
                m.getId(), m.getOrgId(), m.getUserId(), m.getRole(), m.getCreateTime());
    }
}
