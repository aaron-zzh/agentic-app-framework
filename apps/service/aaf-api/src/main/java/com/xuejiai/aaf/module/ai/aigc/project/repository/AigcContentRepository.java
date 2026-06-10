package com.xuejiai.aaf.module.ai.aigc.project.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcContent;

public interface AigcContentRepository
        extends JpaRepository<AigcContent, Long>, JpaSpecificationExecutor<AigcContent> {
    List<AigcContent> findByProjectIdOrderByCreateTimeDesc(Long projectId);

    List<AigcContent> findByProjectIdAndType(Long projectId, String type);
}
