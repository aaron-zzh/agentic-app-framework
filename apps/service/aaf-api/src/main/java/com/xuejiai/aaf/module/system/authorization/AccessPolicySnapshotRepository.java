package com.xuejiai.aaf.module.system.authorization;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AccessPolicySnapshotRepository extends JpaRepository<AccessPolicySnapshot, Long> {

    List<AccessPolicySnapshot> findByPolicyIdInAndPolicyVersionIn(
            Collection<Long> policyIds, Collection<Long> policyVersions);
}
