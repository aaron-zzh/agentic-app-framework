package com.xuejiai.aaf.module.system.sms.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.system.sms.domain.SmsTemplate;

/**
 * @author AaronZZH & Kiro
 */
public interface SmsTemplateRepository extends JpaRepository<SmsTemplate, Long> {

    Optional<SmsTemplate> findByCodeAndStatusAndDeletedFalse(String code, Short status);
}
