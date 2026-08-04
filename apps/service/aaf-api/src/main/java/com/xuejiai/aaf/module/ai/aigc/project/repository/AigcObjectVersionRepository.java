package com.xuejiai.aaf.module.ai.aigc.project.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcObjectVersion;

public interface AigcObjectVersionRepository extends JpaRepository<AigcObjectVersion, Long> {

    List<AigcObjectVersion> findByObjectIdOrderByVersionNoDesc(Long objectId);

    List<AigcObjectVersion> findByObjectIdAndStatus(Long objectId, String status);

    List<AigcObjectVersion> findByExecutionRunIdOrderByIdAsc(Long executionRunId);

    Optional<AigcObjectVersion> findFirstByObjectIdOrderByVersionNoDesc(Long objectId);
}
