package com.xuejiai.aaf.module.system.authorization;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AccessPolicyRepository extends JpaRepository<AccessPolicy, Long> {

    List<AccessPolicy> findByLifecycleInOrderByPriority(Collection<String> lifecycles);

    List<AccessPolicy> findAllByOrderByPriority();
}
