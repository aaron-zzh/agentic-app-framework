package com.xuejiai.aaf.module.ai.aigc.media.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.ai.aigc.media.domain.Media;

import jakarta.persistence.LockModeType;

/** Media 仓储。 */
public interface MediaRepository extends CrudEntityRepository<Media> {

    Optional<Media> findByIdAndUserId(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select media from Media media where media.id = :id and media.userId = :userId")
    Optional<Media> findLockedByIdAndUserId(
            @Param("id") Long id, @Param("userId") Long userId);
}
