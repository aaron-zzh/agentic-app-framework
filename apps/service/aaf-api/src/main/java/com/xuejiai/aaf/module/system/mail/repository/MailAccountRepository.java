package com.xuejiai.aaf.module.system.mail.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.system.mail.domain.MailAccount;

/**
 * @author AaronZZH & Kiro
 */
public interface MailAccountRepository extends JpaRepository<MailAccount, Long> {

    Optional<MailAccount> findByIdAndDeletedFalse(Long id);
}
