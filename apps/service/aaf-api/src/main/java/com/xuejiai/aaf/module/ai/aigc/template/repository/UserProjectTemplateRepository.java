package com.xuejiai.aaf.module.ai.aigc.template.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.xuejiai.aaf.module.ai.aigc.template.domain.UserProjectTemplate;

public interface UserProjectTemplateRepository
        extends JpaRepository<UserProjectTemplate, Long>,
                JpaSpecificationExecutor<UserProjectTemplate> {}
