package com.xuejiai.aaf.module.system.notify.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.xuejiai.aaf.module.system.log.domain.domain.MessageTemplate;

/** 消息模板数据访问层。 */
public interface MessageTemplateRepository
        extends JpaRepository<MessageTemplate, Long>, JpaSpecificationExecutor<MessageTemplate> {

    Optional<MessageTemplate> findByCodeAndDeletedFalse(String code);
}
