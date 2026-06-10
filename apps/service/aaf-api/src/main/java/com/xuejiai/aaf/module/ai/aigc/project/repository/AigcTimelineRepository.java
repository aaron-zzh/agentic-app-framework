package com.xuejiai.aaf.module.ai.aigc.project.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcTimeline;

public interface AigcTimelineRepository
        extends JpaRepository<AigcTimeline, Long>, JpaSpecificationExecutor<AigcTimeline> {
    List<AigcTimeline> findByProjectIdOrderByCreateTimeDesc(Long projectId);
}
