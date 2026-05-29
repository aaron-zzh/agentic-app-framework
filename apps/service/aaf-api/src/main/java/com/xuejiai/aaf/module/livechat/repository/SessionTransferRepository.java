package com.xuejiai.aaf.module.livechat.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.livechat.domain.SessionTransfer;

public interface SessionTransferRepository extends JpaRepository<SessionTransfer, Long> {

    List<SessionTransfer> findBySessionIdOrderByCreateTimeDesc(Long sessionId);
}
