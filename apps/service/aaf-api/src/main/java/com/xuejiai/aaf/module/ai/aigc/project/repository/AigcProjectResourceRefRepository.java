package com.xuejiai.aaf.module.ai.aigc.project.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectResourceRef;

public interface AigcProjectResourceRefRepository
        extends JpaRepository<AigcProjectResourceRef, Long> {

    List<AigcProjectResourceRef> findByProjectIdOrderBySortOrderAscIdAsc(Long projectId);

    Optional<AigcProjectResourceRef> findByProjectIdAndResourceTypeAndResourceId(
            Long projectId, String resourceType, String resourceId);

    boolean existsByProjectIdAndResourceType(Long projectId, String resourceType);

    boolean existsByProjectIdAndResourceTypeAndRoleNot(
            Long projectId, String resourceType, String role);
}
