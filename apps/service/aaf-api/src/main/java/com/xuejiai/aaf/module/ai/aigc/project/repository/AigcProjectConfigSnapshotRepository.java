package com.xuejiai.aaf.module.ai.aigc.project.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectConfigSnapshot;

public interface AigcProjectConfigSnapshotRepository
        extends JpaRepository<AigcProjectConfigSnapshot, Long> {

    Optional<AigcProjectConfigSnapshot> findFirstByProjectIdOrderByRevisionNoDesc(Long projectId);

    List<AigcProjectConfigSnapshot> findByProjectIdOrderByRevisionNoDesc(Long projectId);
}
