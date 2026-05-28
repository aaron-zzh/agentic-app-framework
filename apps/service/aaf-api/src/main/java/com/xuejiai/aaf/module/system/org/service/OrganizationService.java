package com.xuejiai.aaf.module.system.org.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.module.system.org.domain.OrgMember;
import com.xuejiai.aaf.module.system.org.domain.Organization;
import com.xuejiai.aaf.module.system.org.repository.OrgMemberRepository;
import com.xuejiai.aaf.module.system.org.repository.OrganizationRepository;
import com.xuejiai.aaf.module.system.org.vo.OrgMemberAddDTO;
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

    private final OrganizationRepository orgRepository;
    private final OrgMemberRepository memberRepository;

    /** 获取用户所属的所有组织 */
    public List<OrganizationVO> listByUser(Long userId) {
        var memberOrgIds =
                memberRepository.findByUserIdAndDeletedFalse(userId).stream()
                        .map(OrgMember::getOrgId)
                        .toList();
        return orgRepository.findByIdInAndDeletedFalse(memberOrgIds).stream()
                .map(this::toVO)
                .toList();
    }

    public OrganizationVO getById(Long id) {
        return toVO(findOrg(id));
    }

    /** 创建团队组织 */
    @Transactional
    public OrganizationVO create(OrganizationCreateDTO dto, Long currentUserId) {
        if (orgRepository.existsBySlugAndDeletedFalse(dto.slug())) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "组织标识已存在");
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

        return toVO(org);
    }

    @Transactional
    public OrganizationVO update(Long id, OrganizationUpdateDTO dto) {
        var org = findOrg(id);
        if (dto.name() != null) {
            org.setName(dto.name());
        }
        return toVO(orgRepository.save(org));
    }

    @Transactional
    public void delete(Long id) {
        var org = findOrg(id);
        if (org.isPersonal()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "个人工作空间不可删除");
        }
        orgRepository.deleteById(id);
    }

    /** 为新用户创建个人工作空间 */
    @Transactional
    public Organization createPersonalOrg(Long userId, String nickname) {
        var org = new Organization();
        org.setName(nickname + "的空间");
        org.setSlug("personal-" + userId);
        org.setType("personal");
        org.setOwnerId(userId);
        org = orgRepository.save(org);

        var member = new OrgMember();
        member.setOrgId(org.getId());
        member.setUserId(userId);
        member.setRole("owner");
        memberRepository.save(member);

        return org;
    }

    // ==================== 成员管理 ====================

    public List<OrgMemberVO> listMembers(Long orgId) {
        return memberRepository.findByOrgIdAndDeletedFalse(orgId).stream()
                .map(this::toMemberVO)
                .toList();
    }

    @Transactional
    public OrgMemberVO addMember(Long orgId, OrgMemberAddDTO dto) {
        findOrg(orgId); // 确认组织存在
        if (memberRepository.existsByOrgIdAndUserIdAndDeletedFalse(orgId, dto.userId())) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "用户已是组织成员");
        }
        var member = new OrgMember();
        member.setOrgId(orgId);
        member.setUserId(dto.userId());
        member.setRole(dto.role());
        return toMemberVO(memberRepository.save(member));
    }

    @Transactional
    public void removeMember(Long orgId, Long userId) {
        var member =
                memberRepository
                        .findByOrgIdAndUserIdAndDeletedFalse(orgId, userId)
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "成员不存在"));
        if ("owner".equals(member.getRole())) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "不能移除组织所有者");
        }
        memberRepository.deleteById(member.getId());
    }

    // ==================== 私有方法 ====================

    private Organization findOrg(Long id) {
        return orgRepository
                .findById(id)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "组织不存在"));
    }

    private OrganizationVO toVO(Organization org) {
        return new OrganizationVO(
                org.getId(),
                org.getName(),
                org.getSlug(),
                org.getType(),
                org.getOwnerId(),
                org.getCreateTime());
    }

    private OrgMemberVO toMemberVO(OrgMember m) {
        return new OrgMemberVO(
                m.getId(), m.getOrgId(), m.getUserId(), m.getRole(), m.getCreateTime());
    }
}
