package com.xuejiai.aaf.module.ai.aigc.project.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectChannelRef;

public interface AigcProjectChannelRefRepository
        extends JpaRepository<AigcProjectChannelRef, Long> {

    List<AigcProjectChannelRef> findByProjectIdOrderByIdAsc(Long projectId);
}
