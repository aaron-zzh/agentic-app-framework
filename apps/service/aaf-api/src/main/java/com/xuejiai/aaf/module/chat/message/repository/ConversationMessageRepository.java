package com.xuejiai.aaf.module.chat.message.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.chat.message.domain.ConversationMessage;

/**
 * 消息 Repository。
 *
 * @author AaronZZH & Kiro
 */
public interface ConversationMessageRepository extends CrudEntityRepository<ConversationMessage> {

    /** 按会话查询所有消息，按时间升序 */
    List<ConversationMessage> findByConversationIdOrderByCreateTimeAsc(Long conversationId);

    /** 按会话查询对外可见消息，按时间升序 */
    List<ConversationMessage> findByConversationIdAndIsInternalFalseOrderByCreateTimeAsc(
            Long conversationId);

    /** 按会话取最近若干条对外可见消息，按时间倒序返回（调用方需自行反转为正序）。 */
    List<ConversationMessage> findByConversationIdAndIsInternalFalseOrderByCreateTimeDesc(
            Long conversationId, Pageable pageable);
}
