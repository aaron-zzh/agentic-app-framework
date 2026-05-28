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

/**
 * 聊天服务，管理会话和消息
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;

    /**
     * 创建会话
     *
     * @param userId 用户 ID
     * @param dto 创建会话请求
     * @return 会话信息
     */
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

    /**
     * 获取用户的会话列表
     *
     * @param userId 用户 ID
     * @return 会话列表
     */
    @Transactional(readOnly = true)
    public List<ChatSessionVO> listSessions(Long userId) {
        return sessionRepository.findByCreatorIdOrderByUpdateTimeDesc(userId).stream()
                .map(this::toSessionVO)
                .toList();
    }

    /**
     * 获取会话的消息历史
     *
     * @param sessionId 会话 ID
     * @return 消息列表（按时间正序）
     */
    @Transactional(readOnly = true)
    public List<ChatMessageVO> listMessages(Long sessionId) {
        return messageRepository.findBySessionIdOrderByCreateTimeAsc(sessionId).stream()
                .map(this::toMessageVO)
                .toList();
    }

    /**
     * 分页获取会话消息（按时间倒序）
     *
     * @param sessionId 会话 ID
     * @param page 页码（从 0 开始）
     * @param size 每页大小
     * @return 分页消息
     */
    @Transactional(readOnly = true)
    public Page<ChatMessageVO> getMessagesPaged(Long sessionId, int page, int size) {
        return messageRepository
                .findBySessionIdOrderByCreateTimeDesc(sessionId, PageRequest.of(page, size))
                .map(this::toMessageVO);
    }

    /**
     * 归档会话（设置状态为 ARCHIVED）
     *
     * @param sessionId 会话 ID
     */
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

    /**
     * 保存消息
     *
     * @param senderId 发送者 ID
     * @param senderType 发送者类型（HUMAN / AI）
     * @param sessionId 会话 ID
     * @param role 消息角色（user / assistant / system）
     * @param content 消息内容
     * @return 消息信息
     */
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

    /**
     * 保存消息（含 actorType 和用户感知上下文）
     *
     * @param senderId 发送者 ID
     * @param senderType 发送者类型（HUMAN / AI）
     * @param sessionId 会话 ID
     * @param role 消息角色（user / assistant / system）
     * @param content 消息内容
     * @param actorType 行动者类型（human / ai / bot / system）
     * @param awarenessContext 用户感知上下文（JSON）
     * @return 消息信息
     */
    @Transactional
    public ChatMessageVO saveMessage(
            Long senderId,
            String senderType,
            Long sessionId,
            String role,
            String content,
            String actorType,
            String awarenessContext) {
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
        message.setActorType(actorType != null ? actorType : "human");
        message.setAwarenessContext(awarenessContext);
        messageRepository.save(message);
        return toMessageVO(message);
    }

    /**
     * 保存消息（含 Token 计数和元数据）
     *
     * @param senderId 发送者 ID
     * @param senderType 发送者类型（HUMAN / AI）
     * @param sessionId 会话 ID
     * @param role 消息角色（user / assistant / system）
     * @param content 消息内容
     * @param tokenCount Token 消耗数
     * @param metadata 元数据（JSON 格式）
     * @return 消息信息
     */
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

    /**
     * 删除会话（软删除）
     *
     * @param sessionId 会话 ID
     */
    @Transactional
    public void deleteSession(Long sessionId) {
        sessionRepository
                .findById(sessionId)
                .orElseThrow(
                        () -> new BusinessException(ErrorCodeConstants.CHAT_SESSION_NOT_FOUND));
        sessionRepository.deleteById(sessionId);
    }

    /**
     * 重命名会话
     *
     * @param sessionId 会话 ID
     * @param title 新标题
     * @return 更新后的会话信息
     */
    @Transactional
    public ChatSessionVO renameSession(Long sessionId, String title) {
        var session =
                sessionRepository
                        .findById(sessionId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCodeConstants.CHAT_SESSION_NOT_FOUND));
        session.setTitle(title);
        sessionRepository.save(session);
        return toSessionVO(session);
    }

    /**
     * 消息反馈（点赞/点踩）
     *
     * @param messageId 消息 ID
     * @param feedbackType 反馈类型（LIKE/DISLIKE）
     * @param comment 反馈备注
     */
    @Transactional
    public void messageFeedback(Long messageId, String feedbackType, String comment) {
        var message =
                messageRepository
                        .findById(messageId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCodeConstants.CHAT_MESSAGE_NOT_FOUND));
        // 将反馈信息存入 metadata（JSON 格式追加）
        var feedback = "{\"feedback\":\"%s\",\"comment\":\"%s\"}".formatted(feedbackType, comment != null ? comment : "");
        message.setMetadata(feedback);
        messageRepository.save(message);
    }

    /**
     * 累加会话 Token 用量
     *
     * @param sessionId 会话 ID
     * @param tokens 本次消耗的 Token 数
     */
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
