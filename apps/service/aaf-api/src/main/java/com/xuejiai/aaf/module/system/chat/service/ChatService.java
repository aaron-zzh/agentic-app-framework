package com.xuejiai.aaf.module.system.chat.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.module.system.ErrorCodeConstants;
import com.xuejiai.aaf.module.system.chat.domain.ChatMessage;
import com.xuejiai.aaf.module.system.chat.domain.ChatSession;
import com.xuejiai.aaf.module.system.chat.repository.ChatMessageRepository;
import com.xuejiai.aaf.module.system.chat.repository.ChatSessionRepository;
import com.xuejiai.aaf.module.system.chat.vo.ChatMessageVO;
import com.xuejiai.aaf.module.system.chat.vo.ChatSessionCreateDTO;
import com.xuejiai.aaf.module.system.chat.vo.ChatSessionVO;

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

    /** 分页获取会话消息（按时间倒序） */
    @Transactional(readOnly = true)
    public Page<ChatMessageVO> getMessagesPaged(Long sessionId, int page, int size) {
        return messageRepository
                .findBySessionIdOrderByCreateTimeDesc(sessionId, PageRequest.of(page, size))
                .map(this::toMessageVO);
    }

    /** 归档会话（设置状态为 ARCHIVED） */
    @Transactional
    public void archiveSession(Long sessionId) {
        var session =
                sessionRepository
                        .findById(sessionId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCodeConstants.CHAT_SESSION_NOT_FOUND));
        session.setStatus("ARCHIVED");
        sessionRepository.save(session);
    }

    /** 保存消息 */
    @Transactional
    public ChatMessageVO saveMessage(
            Long senderId, String senderType, Long sessionId, String role, String content) {
        sessionRepository
                .findById(sessionId)
                .orElseThrow(
                        () -> new BusinessException(ErrorCodeConstants.CHAT_SESSION_NOT_FOUND));

        var message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setSenderId(senderId);
        message.setSenderType(senderType);
        message.setRole(role);
        message.setContent(content);
        messageRepository.save(message);
        return toMessageVO(message);
    }

    /** 保存消息（含 Token 计数和元数据） */
    @Transactional
    public ChatMessageVO saveMessage(
            Long senderId,
            String senderType,
            Long sessionId,
            String role,
            String content,
            Integer tokenCount,
            String metadata) {
        sessionRepository
                .findById(sessionId)
                .orElseThrow(
                        () -> new BusinessException(ErrorCodeConstants.CHAT_SESSION_NOT_FOUND));

        var message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setSenderId(senderId);
        message.setSenderType(senderType);
        message.setRole(role);
        message.setContent(content);
        message.setTokenCount(tokenCount);
        message.setMetadata(metadata);
        messageRepository.save(message);
        return toMessageVO(message);
    }

    /** 累加会话 Token 用量 */
    @Transactional
    public void addSessionTokens(Long sessionId, long tokens) {
        var session =
                sessionRepository
                        .findById(sessionId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCodeConstants.CHAT_SESSION_NOT_FOUND));
        var current = session.getTotalTokens() != null ? session.getTotalTokens() : 0L;
        session.setTotalTokens(current + tokens);
        sessionRepository.save(session);
    }

    private ChatSessionVO toSessionVO(ChatSession s) {
        return new ChatSessionVO(
                s.getId(),
                s.getTitle(),
                s.getType(),
                s.getStatus(),
                s.getCreatorId(),
                s.getCreateTime(),
                s.getUpdateTime());
    }

    private ChatMessageVO toMessageVO(ChatMessage m) {
        return new ChatMessageVO(
                m.getId(),
                m.getSessionId(),
                m.getSenderId(),
                m.getSenderType(),
                m.getRole(),
                m.getContent(),
                m.getCreateTime());
    }
}
