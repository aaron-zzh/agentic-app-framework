package com.xuejiai.aaf.module.system.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.system.domain.ChatMessage;

/** 聊天消息仓储。 */
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findBySessionIdOrderByCreateTimeAsc(Long sessionId);
}
