package com.xuejiai.aaf.module.ai.aigc.project.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcContentAsset;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcContentAssetId;

public interface AigcContentAssetRepository
        extends JpaRepository<AigcContentAsset, AigcContentAssetId> {
    List<AigcContentAsset> findById_ContentId(Long contentId);

    void deleteById_ContentId(Long contentId);
}
