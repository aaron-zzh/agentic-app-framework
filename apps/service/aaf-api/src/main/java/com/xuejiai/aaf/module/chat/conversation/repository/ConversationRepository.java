package com.xuejiai.aaf.module.chat.conversation.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Query;

import com.xuejiai.aaf.common.enums.chat.ConversationStatusEnum;
import com.xuejiai.aaf.common.enums.chat.ConversationTypeEnum;
import com.xuejiai.aaf.common.enums.chat.ParticipantTypeEnum;
import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.chat.conversation.domain.Conversation;

/**
 * 会话 Repository。
 *
 * @author AaronZZH & Kiro
 */
public interface ConversationRepository extends CrudEntityRepository<Conversation> {

    Optional<Conversation> findByThreadId(String threadId);

    List<Conversation> findByCreatorIdOrderByUpdateTimeDesc(Long creatorId);

    @Query(
            """
            SELECT c FROM Conversation c
            JOIN ConversationParticipant p ON p.conversationId = c.id
            WHERE c.threadId = :threadId
              AND c.orgId = :orgId
              AND c.type = :type
              AND c.status IN :statuses
              AND c.deleted = false
              AND p.participantId = :participantId
              AND p.participantType = :participantType
              AND p.leftAt IS NULL
            """)
    List<Conversation> findVisitorThread(
            String threadId,
            Long orgId,
            ConversationTypeEnum type,
            List<ConversationStatusEnum> statuses,
            String participantId,
            ParticipantTypeEnum participantType);

    @Query(
            """
            SELECT c FROM Conversation c
            JOIN ConversationParticipant p ON p.conversationId = c.id
            WHERE c.orgId = :orgId
              AND c.type = :type
              AND c.status IN :statuses
              AND c.deleted = false
              AND p.participantId = :participantId
              AND p.participantType = :participantType
              AND p.leftAt IS NULL
            ORDER BY c.updateTime DESC, c.id DESC
            """)
    List<Conversation> findVisitorConversations(
            Long orgId,
            ConversationTypeEnum type,
            List<ConversationStatusEnum> statuses,
            String participantId,
            ParticipantTypeEnum participantType);

    // ========== livechat 场景查询 ==========

    /** 查询指定访客未关闭的客服会话 */
    @Query(
            "SELECT c FROM Conversation c WHERE c.creatorId = :creatorId AND c.type = :type AND c.status <> 'CLOSED' AND c.deleted = false")
    Optional<Conversation> findActiveByCreatorIdAndType(Long creatorId, ConversationTypeEnum type);

    /** 按状态+类型查询，按优先级倒序、创建时间升序 */
    List<Conversation> findByStatusAndTypeOrderByPriorityDescCreateTimeAsc(
            ConversationStatusEnum status, ConversationTypeEnum type);

    /** 按坐席 ID 和状态查询 */
    List<Conversation> findByStaffIdAndStatus(Long staffId, ConversationStatusEnum status);

    /** 按状态+类型+更新时间查询（超时处理） */
    List<Conversation> findByStatusAndTypeAndUpdateTimeBefore(
            ConversationStatusEnum status,
            ConversationTypeEnum type,
            LocalDateTime updateTimeBefore);
}
