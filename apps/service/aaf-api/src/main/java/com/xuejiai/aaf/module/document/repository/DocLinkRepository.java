package com.xuejiai.aaf.module.document.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.document.domain.DocLink;

public interface DocLinkRepository extends JpaRepository<DocLink, Long> {

    List<DocLink> findBySourceId(Long sourceId);

    List<DocLink> findByTargetId(Long targetId);

    void deleteBySourceId(Long sourceId);
}
