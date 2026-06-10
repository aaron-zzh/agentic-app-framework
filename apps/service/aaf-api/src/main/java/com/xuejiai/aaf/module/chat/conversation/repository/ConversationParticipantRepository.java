package com.xuejiai.aaf.module.chat.conversation.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.chat.conversation.domain.ConversationParticipant;
import com.xuejiai.aaf.module.chat.enums.ParticipantType;

/**
 * 会话参与方 Repository。
 *
 * @author AaronZZH & Kiro
 */
public interface ConversationParticipantRepository
        extends JpaRepository<ConversationParticipant, Long> {

    List<ConversationParticipant> findByConversationId(Long conversationId);

    /** 查当前活跃参与方（left_at IS NULL） */
    List<ConversationParticipant> findByConversationIdAndLeftAtIsNull(Long conversationId);

    /** 查某参与方当前活跃记录 */
    Optional<ConversationParticipant> findByConversationIdAndParticipantIdAndLeftAtIsNull(
            Long conversationId, String participantId);

    List<ConversationParticipant> findByParticipantIdAndParticipantTypeAndLeftAtIsNull(
            String participantId, ParticipantType participantType);
}
