package com.xuejiai.aaf.module.ai.aigc.media.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.xuejiai.aaf.module.ai.aigc.media.domain.MediaVersion;

/** MediaVersion 仓储。 */
public interface MediaVersionRepository extends JpaRepository<MediaVersion, Long> {

    Optional<MediaVersion> findByIdAndMediaId(Long id, Long mediaId);

    @Query(
            """
            select version
            from MediaVersion version, Media media
            where version.id = :versionId
              and media.id = version.mediaId
              and media.userId = :userId
            """)
    Optional<MediaVersion> findOwnedVersion(
            @Param("versionId") Long versionId, @Param("userId") Long userId);
}
