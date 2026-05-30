package com.xuejiai.aaf.module.livechat.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.livechat.domain.ChatMessage;

public interface LivechatChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /** 查询会话消息（排除内部消息） */
    List<ChatMessage> findBySessionIdAndInternalFalseOrderByCreateTimeAsc(Long sessionId);

    /** 查询会话全部消息（含内部消息，坐席视角） */
    List<ChatMessage> findBySessionIdOrderByCreateTimeAsc(Long sessionId);

    /** 查询最近 N 条消息（用于上下文） */
    List<ChatMessage> findBySessionIdAndInternalFalseOrderByCreateTimeDesc(
            Long sessionId, Pageable pageable);
}
