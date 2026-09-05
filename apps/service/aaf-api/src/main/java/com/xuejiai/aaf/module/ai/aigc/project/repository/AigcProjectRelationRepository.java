package com.xuejiai.aaf.module.ai.aigc.project.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectRelation;

public interface AigcProjectRelationRepository extends JpaRepository<AigcProjectRelation, Long> {

    List<AigcProjectRelation> findByProjectIdOrderByIdAsc(Long projectId);

    boolean existsBySourceObjectIdOrTargetObjectId(Long sourceObjectId, Long targetObjectId);
}
