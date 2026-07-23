package com.xuejiai.aaf.module.system.user.favorite.repository;

import java.util.Optional;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.system.user.favorite.domain.UserFavorite;

public interface UserFavoriteRepository extends CrudEntityRepository<UserFavorite> {

    Optional<UserFavorite> findByUserIdAndTargetTypeAndTargetIdAndDeletedFalse(
            Long userId, String targetType, Long targetId);

    void deleteByUserIdAndTargetTypeAndTargetId(Long userId, String targetType, Long targetId);
}
