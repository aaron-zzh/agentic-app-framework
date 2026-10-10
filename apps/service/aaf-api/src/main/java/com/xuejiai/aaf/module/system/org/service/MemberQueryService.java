package com.xuejiai.aaf.module.system.org.service;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.module.system.org.domain.OrgMember;
import com.xuejiai.aaf.module.system.org.domain.WorkspaceMember;
import com.xuejiai.aaf.module.system.org.repository.OrgMemberRepository;
import com.xuejiai.aaf.module.system.org.repository.WorkspaceMemberRepository;

import lombok.RequiredArgsConstructor;

/**
 * 组织与工作区成员关系的只读查询，供其他业务模块判断用户归属，避免直接访问成员 Repository。
 *
 * <p>仅依赖成员 Repository，不引入授权、权益等服务，可被 CRUD 引用策略等底层流程安全注入。
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberQueryService {

    private final OrgMemberRepository orgMemberRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;

    /** 返回给定用户中属于指定组织的用户 ID。 */
    public Set<Long> findOrgMemberUserIds(Long orgId, Collection<Long> userIds) {
        return orgMemberRepository.findByOrgIdAndUserIdInAndDeletedFalse(orgId, userIds).stream()
                .map(OrgMember::getUserId)
                .collect(Collectors.toSet());
    }

    /** 返回给定用户中属于指定工作区的用户 ID。 */
    public Set<Long> findWorkspaceMemberUserIds(Long workspaceId, Collection<Long> userIds) {
        return workspaceMemberRepository
                .findByWorkspaceIdAndUserIdInAndDeletedFalse(workspaceId, userIds)
                .stream()
                .map(WorkspaceMember::getUserId)
                .collect(Collectors.toSet());
    }
}
