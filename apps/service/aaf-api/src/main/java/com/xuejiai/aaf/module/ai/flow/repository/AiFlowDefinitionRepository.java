package com.xuejiai.aaf.module.ai.flow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.xuejiai.aaf.module.ai.flow.domain.AiFlowDefinition;

public interface AiFlowDefinitionRepository
        extends JpaRepository<AiFlowDefinition, Long>, JpaSpecificationExecutor<AiFlowDefinition> {
}
