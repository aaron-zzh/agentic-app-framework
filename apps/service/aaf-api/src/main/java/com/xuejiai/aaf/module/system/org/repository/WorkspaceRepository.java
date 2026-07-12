package com.xuejiai.aaf.module.system.org.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.xuejiai.aaf.module.system.org.domain.Workspace;

/**
 * 工作区仓储。
 *
 * @author AaronZZH & Kiro
 */
public interface WorkspaceRepository
        extends JpaRepository<Workspace, Long>, JpaSpecificationExecutor<Workspace> {

    Optional<Workspace> findByOrgIdAndSlugAndDeletedFalse(Long orgId, String slug);

    List<Workspace> findByOrgIdAndDeletedFalse(Long orgId);
}
