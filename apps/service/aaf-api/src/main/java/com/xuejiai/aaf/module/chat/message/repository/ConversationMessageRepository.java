package com.xuejiai.aaf.module.chat.message.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * 按会话与 AG-UI 外部 messageId（{@code payload->>'aguiMessageId'}）精确查找消息（AAF-114 #11412）。
     *
     * <p>{@code aguiMessageId} 是前端 assistant-ui {@code ThreadMessage.id}，与数据库自增 {@code id} 是两套独立
     * 体系，只能通过写入时记录的 {@code payload} JSONB 字段反查，不能直接按主键查询。
     */
    @Query(
            value =
                    "SELECT * FROM conversation_message "
                            + "WHERE conversation_id = :conversationId "
                            + "AND payload ->> 'aguiMessageId' = :aguiMessageId "
                            + "AND deleted = false "
                            + "LIMIT 1",
            nativeQuery = true)
    Optional<ConversationMessage> findByConversationIdAndAguiMessageId(
            @Param("conversationId") Long conversationId,
            @Param("aguiMessageId") String aguiMessageId);
}
