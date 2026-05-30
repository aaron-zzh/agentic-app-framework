package com.xuejiai.aaf.module.system.profile.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.system.profile.domain.UserProfile;

/** 用户画像主表数据访问层。 */
public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    Optional<UserProfile> findByUserIdAndDeletedFalse(Long userId);
}
