package com.xuejiai.aaf.module.system.org.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.system.org.domain.WorkspaceMember;

/**
 * 工作区成员仓储。
 *
 * @author AaronZZH & Kiro
 */
public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, Long> {

    List<WorkspaceMember> findByWorkspaceIdAndDeletedFalse(Long workspaceId);

    List<WorkspaceMember> findByUserIdAndDeletedFalse(Long userId);

    List<WorkspaceMember> findByOrgIdAndUserIdAndDeletedFalse(Long orgId, Long userId);

    Optional<WorkspaceMember> findByWorkspaceIdAndUserIdAndDeletedFalse(
            Long workspaceId, Long userId);

    List<WorkspaceMember> findByWorkspaceIdAndUserIdInAndDeletedFalse(
            Long workspaceId, java.util.Collection<Long> userIds);

    boolean existsByWorkspaceIdAndUserIdAndDeletedFalse(Long workspaceId, Long userId);
}
