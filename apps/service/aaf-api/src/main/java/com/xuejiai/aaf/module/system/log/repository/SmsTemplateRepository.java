package com.xuejiai.aaf.module.system.log.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.system.log.domain.SmsTemplate;

public interface SmsTemplateRepository extends JpaRepository<SmsTemplate, Long> {

    Optional<SmsTemplate> findByCodeAndStatusAndDeletedFalse(String code, Short status);
}
