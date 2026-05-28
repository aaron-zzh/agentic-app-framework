package com.xuejiai.aaf.module.system.org.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.system.org.domain.Organization;

/**
 * 组织仓储。
 *
 * @author AaronZZH & Kiro
 */
public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    Optional<Organization> findBySlugAndDeletedFalse(String slug);

    boolean existsBySlugAndDeletedFalse(String slug);

    Optional<Organization> findByOwnerIdAndTypeAndDeletedFalse(Long ownerId, String type);

    List<Organization> findByIdInAndDeletedFalse(List<Long> ids);
}
