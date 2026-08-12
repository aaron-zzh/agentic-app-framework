package com.xuejiai.aaf.module.ai.aigc.media.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.ai.aigc.media.domain.AigcMedia;
import com.xuejiai.aaf.module.ai.aigc.media.enums.AigcMediaSourceType;

import jakarta.persistence.LockModeType;

/** AIGC 媒体仓储。 */
public interface AigcMediaRepository extends CrudEntityRepository<AigcMedia> {
    Optional<AigcMedia> findByIdAndUserId(Long id, Long userId);

    List<AigcMedia> findByOriginalProjectIdAndSourceTypeInOrderByIdAsc(
            Long originalProjectId, List<AigcMediaSourceType> sourceTypes);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select media from AigcMedia media where media.id = :id and media.userId = :userId")
    Optional<AigcMedia> findLockedByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
}
