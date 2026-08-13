package com.xuejiai.aaf.module.system.org.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Query(
            """
            select member.workspaceId
              from WorkspaceMember member
             where member.orgId = :orgId
               and member.userId = :userId
               and member.deleted = false
            """)
    List<Long> findWorkspaceIdsByOrgIdAndUserId(
            @Param("orgId") Long orgId, @Param("userId") Long userId);

    @Query(
            """
            select member.workspaceId
              from WorkspaceMember member
             where member.userId = :userId
               and member.deleted = false
            """)
    List<Long> findWorkspaceIdsByUserId(@Param("userId") Long userId);

    Optional<WorkspaceMember> findByWorkspaceIdAndUserIdAndDeletedFalse(
            Long workspaceId, Long userId);

    List<WorkspaceMember> findByWorkspaceIdAndUserIdInAndDeletedFalse(
            Long workspaceId, java.util.Collection<Long> userIds);

    boolean existsByWorkspaceIdAndUserIdAndDeletedFalse(Long workspaceId, Long userId);
}
