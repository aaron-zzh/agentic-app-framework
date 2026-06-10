package com.xuejiai.aaf.module.chat.conversation.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import com.xuejiai.aaf.module.chat.conversation.domain.Conversation;
import com.xuejiai.aaf.module.chat.enums.ConversationStatus;
import com.xuejiai.aaf.module.chat.enums.ConversationType;

/**
 * 会话 Repository。
 *
 * @author AaronZZH & Kiro
 */
public interface ConversationRepository
        extends JpaRepository<Conversation, Long>, JpaSpecificationExecutor<Conversation> {

    Optional<Conversation> findByThreadId(String threadId);

    List<Conversation> findByCreatorIdOrderByUpdateTimeDesc(Long creatorId);

    // ========== livechat 场景查询 ==========

    /** 查询指定访客未关闭的客服会话 */
    @Query(
            "SELECT c FROM Conversation c WHERE c.creatorId = :creatorId AND c.type = :type AND c.status <> 'CLOSED' AND c.deleted = false")
    Optional<Conversation> findActiveByCreatorIdAndType(Long creatorId, ConversationType type);

    /** 按状态+类型查询，按优先级倒序、创建时间升序 */
    List<Conversation> findByStatusAndTypeOrderByPriorityDescCreateTimeAsc(
            ConversationStatus status, ConversationType type);

    /** 按坐席 ID 和状态查询 */
    List<Conversation> findByStaffIdAndStatus(Long staffId, ConversationStatus status);

    /** 按状态+类型+更新时间查询（超时处理） */
    List<Conversation> findByStatusAndTypeAndUpdateTimeBefore(
            ConversationStatus status, ConversationType type, LocalDateTime updateTimeBefore);
}
