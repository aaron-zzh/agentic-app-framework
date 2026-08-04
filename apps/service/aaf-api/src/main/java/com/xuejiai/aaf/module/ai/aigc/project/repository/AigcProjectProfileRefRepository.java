package com.xuejiai.aaf.module.ai.aigc.project.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectProfileRef;

public interface AigcProjectProfileRefRepository
        extends JpaRepository<AigcProjectProfileRef, Long> {

    List<AigcProjectProfileRef> findByProjectIdOrderByIdAsc(Long projectId);
}
