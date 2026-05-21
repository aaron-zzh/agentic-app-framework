package com.xuejiai.aaf.framework.intelligent.core.model;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ModelPreferenceRepository extends JpaRepository<ModelPreference, Long> {

    /** 查用户偏好 */
    Optional<ModelPreference> findByScopeAndScopeIdAndCapability(
            String scope, Long scopeId, String capability);

    /** 查系统默认 */
    Optional<ModelPreference> findByScopeAndScopeIdIsNullAndCapability(
            String scope, String capability);
}
