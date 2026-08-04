package com.xuejiai.aaf.module.ai.aigc.brand.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.ai.aigc.brand.domain.AigcBrandProfileDocumentRef;

/** 品牌版本文档引用内部仓储。 */
public interface AigcBrandProfileDocumentRefRepository
        extends JpaRepository<AigcBrandProfileDocumentRef, Long> {

    List<AigcBrandProfileDocumentRef> findByBrandProfileVersionIdAndDeletedFalseOrderBySortOrder(
            Long brandProfileVersionId);
}
