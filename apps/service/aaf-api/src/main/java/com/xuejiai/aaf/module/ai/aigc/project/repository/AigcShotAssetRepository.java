package com.xuejiai.aaf.module.ai.aigc.project.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcShotAsset;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcShotAssetId;

public interface AigcShotAssetRepository extends JpaRepository<AigcShotAsset, AigcShotAssetId> {
    List<AigcShotAsset> findById_ShotId(Long shotId);

    void deleteById_ShotId(Long shotId);
}
