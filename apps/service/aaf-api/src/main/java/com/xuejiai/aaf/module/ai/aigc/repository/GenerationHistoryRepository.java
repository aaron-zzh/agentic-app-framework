package com.xuejiai.aaf.module.ai.aigc.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.ai.aigc.domain.GenerationHistory;
import com.xuejiai.aaf.module.ai.aigc.enums.MediaAssetType;

public interface GenerationHistoryRepository extends JpaRepository<GenerationHistory, Long> {

    Page<GenerationHistory> findByUserId(Long userId, Pageable pageable);

    Page<GenerationHistory> findByUserIdAndType(
            Long userId, MediaAssetType type, Pageable pageable);
}
