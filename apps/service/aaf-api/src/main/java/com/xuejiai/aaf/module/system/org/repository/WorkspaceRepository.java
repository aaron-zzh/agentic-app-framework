package com.xuejiai.aaf.module.system.org.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.system.org.domain.Workspace;

/**
 * 工作区仓储。
 *
 * @author AaronZZH & Kiro
 */
public interface WorkspaceRepository extends CrudEntityRepository<Workspace> {

    Optional<Workspace> findByOrgIdAndSlugAndDeletedFalse(Long orgId, String slug);

    List<Workspace> findByOrgIdAndDeletedFalse(Long orgId);

    List<Workspace> findByOrgIdInAndDeletedFalse(Collection<Long> orgIds);

    boolean existsByOrgIdAndOwnerIdAndDeletedFalse(Long orgId, Long ownerId);
}
