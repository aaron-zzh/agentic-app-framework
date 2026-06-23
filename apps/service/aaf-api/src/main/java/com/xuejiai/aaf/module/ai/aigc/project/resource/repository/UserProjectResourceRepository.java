package com.xuejiai.aaf.module.ai.aigc.project.resource.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.ai.aigc.project.resource.domain.UserProjectResource;

public interface UserProjectResourceRepository extends JpaRepository<UserProjectResource, Long> {

    List<UserProjectResource> findByProjectIdAndDeletedFalseOrderBySortOrder(Long projectId);

    boolean existsByProjectIdAndResourceTypeAndResourceIdAndDeletedFalse(
            Long projectId, String resourceType, Long resourceId);
}
