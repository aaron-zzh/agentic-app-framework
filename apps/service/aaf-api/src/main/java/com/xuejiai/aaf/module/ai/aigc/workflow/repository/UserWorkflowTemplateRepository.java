package com.xuejiai.aaf.module.ai.aigc.workflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.xuejiai.aaf.module.ai.aigc.workflow.domain.UserWorkflowTemplate;

public interface UserWorkflowTemplateRepository
        extends JpaRepository<UserWorkflowTemplate, Long>,
                JpaSpecificationExecutor<UserWorkflowTemplate> {}
