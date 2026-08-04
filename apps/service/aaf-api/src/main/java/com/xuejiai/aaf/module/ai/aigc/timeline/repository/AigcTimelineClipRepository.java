package com.xuejiai.aaf.module.ai.aigc.timeline.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.ai.aigc.timeline.domain.AigcTimelineClip;

public interface AigcTimelineClipRepository extends JpaRepository<AigcTimelineClip, Long> {

    List<AigcTimelineClip> findByTrackIdInOrderByTrackIdAscPositionMsAscIdAsc(
            Collection<Long> trackIds);

    void deleteByTrackIdIn(Collection<Long> trackIds);
}
