package com.xuejiai.aaf.module.ai.aigc.project.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProject;

public interface AigcProjectRepository
        extends JpaRepository<AigcProject, Long>, JpaSpecificationExecutor<AigcProject> {
    Page<AigcProject> findByUserId(Long userId, Pageable pageable);

    List<AigcProject> findByUserIdAndStatus(Long userId, String status);
}
