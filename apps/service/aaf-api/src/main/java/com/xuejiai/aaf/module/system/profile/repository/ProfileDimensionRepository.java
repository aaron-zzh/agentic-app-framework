package com.xuejiai.aaf.module.system.profile.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.system.profile.domain.ProfileDimension;

/** 画像维度定义数据访问层。 */
public interface ProfileDimensionRepository extends JpaRepository<ProfileDimension, Long> {

    Optional<ProfileDimension> findByCodeAndDeletedFalse(String code);

    List<ProfileDimension> findByGroupCodeAndStatusAndDeletedFalseOrderBySortOrder(
            String groupCode, Integer status);

    List<ProfileDimension> findByStatusAndDeletedFalseOrderBySortOrder(Integer status);

    List<ProfileDimension> findByAiVisibleTrueAndStatusAndDeletedFalse(Integer status);
}
