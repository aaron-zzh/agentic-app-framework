package com.xuejiai.aaf.module.ai.aigc.project.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectRevision;

public interface AigcProjectRevisionRepository extends JpaRepository<AigcProjectRevision, Long> {

    List<AigcProjectRevision> findByProjectIdOrderByRevisionNoDesc(Long projectId);
}
