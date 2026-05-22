package com.xuejiai.aaf.autodev.doc.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.autodev.doc.domain.AutodevDocLink;

public interface AutodevDocLinkRepository extends JpaRepository<AutodevDocLink, Long> {

    List<AutodevDocLink> findBySourceId(Long sourceId);

    List<AutodevDocLink> findByTargetId(Long targetId);

    void deleteBySourceId(Long sourceId);
}
