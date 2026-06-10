package com.xuejiai.aaf.module.ai.aigc.project.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcClip;

public interface AigcClipRepository extends JpaRepository<AigcClip, Long> {
    List<AigcClip> findByTrackIdOrderByPositionMs(Long trackId);

    List<AigcClip> findByShotId(Long shotId);

    void deleteByTrackId(Long trackId);
}
