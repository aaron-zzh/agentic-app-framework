package com.xuejiai.aaf.module.system.mail.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.system.mail.domain.MailTemplate;

/**
 * @author AaronZZH & Kiro
 */
public interface MailTemplateRepository extends JpaRepository<MailTemplate, Long> {

    Optional<MailTemplate> findByCodeAndDeletedFalse(String code);

    Optional<MailTemplate> findByIdAndDeletedFalse(Long id);
}
