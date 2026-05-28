package com.xuejiai.aaf.module.system.org.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.system.org.domain.OrgMember;

/**
 * 组织成员仓储。
 *
 * @author AaronZZH & Kiro
 */
public interface OrgMemberRepository extends JpaRepository<OrgMember, Long> {

    List<OrgMember> findByUserIdAndDeletedFalse(Long userId);

    List<OrgMember> findByOrgIdAndDeletedFalse(Long orgId);

    Optional<OrgMember> findByOrgIdAndUserIdAndDeletedFalse(Long orgId, Long userId);

    boolean existsByOrgIdAndUserIdAndDeletedFalse(Long orgId, Long userId);
}
