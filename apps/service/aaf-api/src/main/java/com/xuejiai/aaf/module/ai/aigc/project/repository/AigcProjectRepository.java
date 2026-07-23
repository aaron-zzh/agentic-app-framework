package com.xuejiai.aaf.module.ai.aigc.project.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProject;

public interface AigcProjectRepository extends CrudEntityRepository<AigcProject> {
    Page<AigcProject> findByUserId(Long userId, Pageable pageable);

    List<AigcProject> findByUserIdAndStatus(Long userId, String status);
}
