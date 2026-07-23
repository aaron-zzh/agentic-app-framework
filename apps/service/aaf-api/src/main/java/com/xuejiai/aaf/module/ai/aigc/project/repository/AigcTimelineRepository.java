package com.xuejiai.aaf.module.ai.aigc.project.repository;

import java.util.List;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcTimeline;

public interface AigcTimelineRepository extends CrudEntityRepository<AigcTimeline> {
    List<AigcTimeline> findByProjectIdOrderByCreateTimeDesc(Long projectId);
}
