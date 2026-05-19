package com.xuejiai.aaf.module.system.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.module.system.ErrorCodeConstants;
import com.xuejiai.aaf.module.system.domain.ChatMessage;
import com.xuejiai.aaf.module.system.domain.ChatSession;
import com.xuejiai.aaf.module.system.repository.ChatMessageRepository;
import com.xuejiai.aaf.module.system.repository.ChatSessionRepository;
import com.xuejiai.aaf.module.system.vo.ChatMessageVO;
import com.xuejiai.aaf.module.system.vo.ChatSessionCreateDTO;
import com.xuejiai.aaf.module.system.vo.ChatSessionVO;

import lombok.RequiredArgsConstructor;

/** 聊天服务，管理会话和消息。 */
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;

    /** 创建会话 */
    @Transactional
    public ChatSessionVO createSession(Long userId, ChatSessionCreateDTO dto) {
        var session = new ChatSession();
        session.setTitle(dto.title());
        session.setType(dto.type() != null ? dto.type() : "LIVECHAT");
        session.setStatus("ACTIVE");
        session.setCreatorId(userId);
        sessionRepository.save(session);
        return toSessionVO(session);
    }

    /** 获取用户的会话列表 */
    @Transactional(readOnly = true)
    public List<ChatSessionVO> listSessions(Long userId) {
        return sessionRepository.findByCreatorIdOrderByUpdateTimeDesc(userId).stream()
                .map(this::toSessionVO)
                .toList();
    }

    /** 获取会话的消息历史 */
    @Transactional(readOnly = true)
    public List<ChatMessageVO> listMessages(Long sessionId) {
        return messageRepository.findBySessionIdOrderByCreateTimeAsc(sessionId).stream()
                .map(this::toMessageVO)
                .toList();
    }

    /** 保存消息 */
    @Transactional
    public ChatMessageVO saveMessage(Long senderId, String senderType, Long sessionId, String role, String content) {
        // 校验会话存在
        sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCodeConstants.CHAT_SESSION_NOT_FOUND));

        var message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setSenderId(senderId);
        message.setSenderType(senderType);
        message.setRole(role);
        message.setContent(content);
        messageRepository.save(message);
        return toMessageVO(message);
    }

    private ChatSessionVO toSessionVO(ChatSession s) {
        return new ChatSessionVO(
                s.getId(), s.getTitle(), s.getType(), s.getStatus(),
                s.getCreatorId(), s.getCreateTime(), s.getUpdateTime());
    }

    private ChatMessageVO toMessageVO(ChatMessage m) {
        return new ChatMessageVO(
                m.getId(), m.getSessionId(), m.getSenderId(), m.getSenderType(),
                m.getRole(), m.getContent(), m.getCreateTime());
    }
}
