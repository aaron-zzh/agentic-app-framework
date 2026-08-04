package com.xuejiai.aaf.module.ai.aigc.media.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.xuejiai.aaf.module.ai.aigc.media.domain.AigcMediaVersion;

/** AIGC 媒体版本内部仓储，不暴露 BaseCrud。 */
public interface AigcMediaVersionRepository extends JpaRepository<AigcMediaVersion, Long> {
    Optional<AigcMediaVersion> findByIdAndMediaId(Long id, Long mediaId);

    @Query(
            """
            select version
            from AigcMediaVersion version, AigcMedia media
            where version.id = :versionId
              and media.id = version.mediaId
              and media.userId = :userId
            """)
    Optional<AigcMediaVersion> findOwnedVersion(
            @Param("versionId") Long versionId, @Param("userId") Long userId);
}
