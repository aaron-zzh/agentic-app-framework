package com.xuejiai.aaf.module.system.notify.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.xuejiai.aaf.module.system.notify.domain.Notice;

/**
 * @author AaronZZH & Kiro
 */
public interface NoticeRepository
        extends JpaRepository<Notice, Long>, JpaSpecificationExecutor<Notice> {}
