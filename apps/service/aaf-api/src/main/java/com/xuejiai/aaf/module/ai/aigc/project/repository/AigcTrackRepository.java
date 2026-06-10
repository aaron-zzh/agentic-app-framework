package com.xuejiai.aaf.module.ai.aigc.project.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcTrack;

public interface AigcTrackRepository extends JpaRepository<AigcTrack, Long> {
    List<AigcTrack> findByTimelineIdOrderBySortOrder(Long timelineId);

    void deleteByTimelineId(Long timelineId);
}
