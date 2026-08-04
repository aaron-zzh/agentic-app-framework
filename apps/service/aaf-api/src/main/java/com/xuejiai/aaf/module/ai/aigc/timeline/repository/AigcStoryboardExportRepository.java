package com.xuejiai.aaf.module.ai.aigc.timeline.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.ai.aigc.timeline.domain.AigcStoryboardExport;

public interface AigcStoryboardExportRepository extends JpaRepository<AigcStoryboardExport, Long> {

    List<AigcStoryboardExport> findByProjectIdOrderBySourceRevisionNoDescIdDesc(Long projectId);
}
