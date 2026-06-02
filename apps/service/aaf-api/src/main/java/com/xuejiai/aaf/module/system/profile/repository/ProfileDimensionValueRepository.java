package com.xuejiai.aaf.module.system.profile.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.system.profile.domain.ProfileDimensionValue;

/** 用户画像维度值数据访问层。 */
public interface ProfileDimensionValueRepository
        extends JpaRepository<ProfileDimensionValue, Long> {

    List<ProfileDimensionValue> findByUserIdAndDeletedFalse(Long userId);

    Optional<ProfileDimensionValue> findByUserIdAndDimensionIdAndDeletedFalse(
            Long userId, Long dimensionId);

    List<ProfileDimensionValue> findByUserIdAndDimensionIdInAndDeletedFalse(
            Long userId, List<Long> dimensionIds);
}
