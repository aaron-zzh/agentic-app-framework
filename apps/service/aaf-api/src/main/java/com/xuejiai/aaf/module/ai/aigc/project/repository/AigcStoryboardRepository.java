package com.xuejiai.aaf.module.ai.aigc.project.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcStoryboard;

public interface AigcStoryboardRepository
        extends JpaRepository<AigcStoryboard, Long>, JpaSpecificationExecutor<AigcStoryboard> {
    List<AigcStoryboard> findByProjectIdOrderByCreateTimeDesc(Long projectId);
}
