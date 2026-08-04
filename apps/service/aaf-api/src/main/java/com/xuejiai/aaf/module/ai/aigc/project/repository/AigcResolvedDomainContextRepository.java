package com.xuejiai.aaf.module.ai.aigc.project.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcResolvedDomainContext;

public interface AigcResolvedDomainContextRepository
        extends JpaRepository<AigcResolvedDomainContext, Long> {

    List<AigcResolvedDomainContext> findByProjectIdOrderByRevisionNoDesc(Long projectId);
}
