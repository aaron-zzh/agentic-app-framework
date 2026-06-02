package com.xuejiai.aaf.module.system.role.policy;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 访问策略仓储。
 *
 * @author AaronZZH & Kiro
 */
public interface AccessPolicyRepository extends JpaRepository<AccessPolicy, Long> {

    List<AccessPolicy> findByStatusOrderByPriority(Integer status);

    List<AccessPolicy> findByTargetResourceAndStatusOrderByPriority(
            String targetResource, Integer status);
}
