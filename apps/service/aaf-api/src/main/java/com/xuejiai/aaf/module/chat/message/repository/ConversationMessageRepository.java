package com.xuejiai.aaf.module.chat.message.repository;

import java.util.List;

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
}
