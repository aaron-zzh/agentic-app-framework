package com.xuejiai.aaf.module.system.chat.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.system.chat.domain.ChatSession;

/** 聊天会话仓储。 */
public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    List<ChatSession> findByCreatorIdOrderByUpdateTimeDesc(Long creatorId);
}
