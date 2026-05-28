package com.xuejiai.aaf.module.system.chat.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.system.chat.domain.ChatMessage;

/**
 * 聊天消息仓储
 *
 * @author AaronZZH & Kiro
 */
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findBySessionIdOrderByCreateTimeAsc(Long sessionId);

    Page<ChatMessage> findBySessionIdOrderByCreateTimeDesc(Long sessionId, Pageable pageable);
}
