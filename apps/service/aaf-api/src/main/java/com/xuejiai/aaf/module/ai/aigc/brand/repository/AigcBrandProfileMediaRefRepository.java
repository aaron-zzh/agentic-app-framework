package com.xuejiai.aaf.module.ai.aigc.brand.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.ai.aigc.brand.domain.AigcBrandProfileMediaRef;

/** 品牌版本媒体引用内部仓储。 */
public interface AigcBrandProfileMediaRefRepository
        extends JpaRepository<AigcBrandProfileMediaRef, Long> {

    List<AigcBrandProfileMediaRef> findByBrandProfileVersionIdAndDeletedFalseOrderBySortOrder(
            Long brandProfileVersionId);
}
