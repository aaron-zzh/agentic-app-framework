package com.xuejiai.aaf.module.ai.aigc.brand.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.xuejiai.aaf.module.ai.aigc.brand.domain.AigcBrandProfileVersion;

/** 品牌资料版本内部仓储。 */
public interface AigcBrandProfileVersionRepository
        extends JpaRepository<AigcBrandProfileVersion, Long> {

    Optional<AigcBrandProfileVersion> findByIdAndBrandProfileIdAndDeletedFalse(
            Long id, Long brandProfileId);

    List<AigcBrandProfileVersion> findByBrandProfileIdAndDeletedFalseOrderByVersionNoDesc(
            Long brandProfileId);

    boolean existsByBrandProfileIdAndDeletedFalse(Long brandProfileId);

    @Query(
            "select coalesce(max(v.versionNo), 0) from AigcBrandProfileVersion v where v.brandProfileId = :profileId and v.deleted = false")
    int findMaxVersionNo(@Param("profileId") Long profileId);
}
