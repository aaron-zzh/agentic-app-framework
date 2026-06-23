package com.xuejiai.aaf.module.system.user.favorite.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.xuejiai.aaf.module.system.user.favorite.domain.UserFavorite;

public interface UserFavoriteRepository
        extends JpaRepository<UserFavorite, Long>, JpaSpecificationExecutor<UserFavorite> {

    Optional<UserFavorite> findByUserIdAndTargetTypeAndTargetIdAndDeletedFalse(
            Long userId, String targetType, Long targetId);

    void deleteByUserIdAndTargetTypeAndTargetId(Long userId, String targetType, Long targetId);
}
