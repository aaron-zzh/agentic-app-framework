package com.xuejiai.aaf.module.ai.aigc.project.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectMediaRef;

public interface AigcProjectMediaRefRepository extends JpaRepository<AigcProjectMediaRef, Long> {

    List<AigcProjectMediaRef> findByProjectIdOrderBySortOrderAscIdAsc(Long projectId);

    List<AigcProjectMediaRef> findByProjectIdAndMediaVersionIdAndRole(
            Long projectId, Long mediaVersionId, String role);
}
