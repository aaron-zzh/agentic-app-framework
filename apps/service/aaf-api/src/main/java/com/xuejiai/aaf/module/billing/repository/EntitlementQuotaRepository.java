package com.xuejiai.aaf.module.billing.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.billing.domain.EntitlementQuota;

public interface EntitlementQuotaRepository extends CrudEntityRepository<EntitlementQuota> {

    Optional<EntitlementQuota> findByUserIdAndEntId(Long userId, Long entId);

    List<EntitlementQuota> findByUserId(Long userId);

    @org.springframework.data.jpa.repository.Query(
            "SELECT q FROM EntitlementQuota q JOIN EntitlementDef d ON q.entId = d.id "
                    + "WHERE q.userId = :userId AND d.code = :code AND q.deleted = false")
    Optional<EntitlementQuota> findByUserIdAndEntCode(
            @org.springframework.data.repository.query.Param("userId") Long userId,
            @org.springframework.data.repository.query.Param("code") String code);

    List<EntitlementQuota> findByNextResetAtLessThanEqual(LocalDateTime now);
}
