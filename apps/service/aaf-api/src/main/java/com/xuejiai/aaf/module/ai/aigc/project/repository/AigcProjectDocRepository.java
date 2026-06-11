package com.xuejiai.aaf.module.ai.aigc.project.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectDoc;

/** AIGC 项目-文档关联仓储。 */
public interface AigcProjectDocRepository extends JpaRepository<AigcProjectDoc, Long> {

    List<AigcProjectDoc> findByProjectIdOrderBySortOrder(Long projectId);

    Optional<AigcProjectDoc> findByProjectIdAndDocId(Long projectId, Long docId);

    @Modifying
    @Transactional
    void deleteByProjectIdAndDocId(Long projectId, Long docId);
}
