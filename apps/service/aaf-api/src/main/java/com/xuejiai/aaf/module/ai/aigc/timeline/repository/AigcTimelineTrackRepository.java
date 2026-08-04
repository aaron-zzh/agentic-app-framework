package com.xuejiai.aaf.module.ai.aigc.timeline.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.ai.aigc.timeline.domain.AigcTimelineTrack;

public interface AigcTimelineTrackRepository extends JpaRepository<AigcTimelineTrack, Long> {

    List<AigcTimelineTrack> findByCompositionIdOrderBySortOrderAscIdAsc(Long compositionId);

    void deleteByCompositionId(Long compositionId);

    void deleteByIdIn(Collection<Long> ids);
}
