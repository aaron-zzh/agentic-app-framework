package com.xuejiai.aaf.module.system.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.system.domain.Organization;

/** 组织仓储。 */
public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    Optional<Organization> findBySlugAndDeletedFalse(String slug);

    boolean existsBySlugAndDeletedFalse(String slug);

    Optional<Organization> findByOwnerIdAndTypeAndDeletedFalse(Long ownerId, String type);

    List<Organization> findByIdInAndDeletedFalse(List<Long> ids);
}
