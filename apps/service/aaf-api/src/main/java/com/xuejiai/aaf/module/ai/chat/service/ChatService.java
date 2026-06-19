package com.xuejiai.aaf.module.ai.chat.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.enums.chat.ConversationStatusEnum;
import com.xuejiai.aaf.common.enums.chat.ConversationTypeEnum;
import com.xuejiai.aaf.common.enums.chat.MessageSenderTypeEnum;
import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.module.ai.chat.vo.ChatMessageVO;
import com.xuejiai.aaf.module.ai.chat.vo.ChatSessionCreateDTO;
import com.xuejiai.aaf.module.ai.chat.vo.ChatSessionVO;
import com.xuejiai.aaf.module.chat.conversation.domain.Conversation;
import com.xuejiai.aaf.module.chat.conversation.repository.ConversationRepository;
import com.xuejiai.aaf.module.chat.message.domain.ConversationMessage;
import com.xuejiai.aaf.module.chat.message.repository.ConversationMessageRepository;
import com.xuejiai.aaf.module.system.ErrorCodeConstants;

import lombok.RequiredArgsConstructor;

/**
 * 聊天服务，管理会话和消息。
 *
 * <p>底层存储已迁移至 module/chat 新实体（Conversation / ConversationMessage）， 对外保持原有方法签名不变。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository messageRepository;

    /**
     * 创建会话
     *
     * @param userId 用户 ID
     * @param dto 创建会话请求
     * @return 会话信息
     */
    @Transactional
    public ChatSessionVO createSession(Long userId, ChatSessionCreateDTO dto) {
        var conv = new Conversation();
        conv.setTitle(dto.title());
        conv.setType(parseType(dto.type()));
        conv.setStatus(ConversationStatusEnum.ACTIVE);
        conv.setCreatorId(userId);
        conv.setThreadId(java.util.UUID.randomUUID().toString());
        conversationRepository.save(conv);
        return toSessionVO(conv);
    }

    /**
     * 获取用户的会话列表
     *
     * @param userId 用户 ID
     * @return 会话列表
     */
    @Transactional(readOnly = true)
    public List<ChatSessionVO> listSessions(Long userId) {
        return conversationRepository.findByCreatorIdOrderByUpdateTimeDesc(userId).stream()
                .map(this::toSessionVO)
                .toList();
    }

    /**
     * 获取会话的消息历史（按时间正序）
     *
     * @param sessionId 会话 ID
     * @return 消息列表
     */
    @Transactional(readOnly = true)
    public List<ChatMessageVO> listMessages(Long sessionId) {
        return messageRepository.findByConversationIdOrderByCreateTimeAsc(sessionId).stream()
                .map(this::toMessageVO)
                .toList();
    }

    /** 按 threadId 查消息（AG-UI 链路使用） */
    @Transactional(readOnly = true)
    public List<ChatMessageVO> listMessagesByThreadId(String threadId) {
        return conversationRepository
                .findByThreadId(threadId)
                .map(conv -> listMessages(conv.getId()))
                .orElse(List.of());
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
        // ConversationMessageRepository 暂不提供分页方法，用 findAll + Specification 替代
        // TODO: ConversationMessageRepository 增加分页查询方法后移除此处的内存分页
        var all = messageRepository.findByConversationIdOrderByCreateTimeAsc(sessionId);
        var total = all.size();
        var from = Math.min(page * size, total);
        var to = Math.min(from + size, total);
        var slice = all.subList(from, to);
        var vos = slice.stream().map(this::toMessageVO).toList();
        return new org.springframework.data.domain.PageImpl<>(
                vos, PageRequest.of(page, size), total);
    }

    /**
     * 归档会话（设置状态为 ARCHIVED）
     *
     * @param sessionId 会话 ID
     */
    @Transactional
    public void archiveSession(Long sessionId) {
        var conv = requireConversation(sessionId);
        conv.setStatus(ConversationStatusEnum.ARCHIVED);
        conversationRepository.save(conv);
    }

    /**
     * 保存消息
     *
     * @param senderId 发送者 ID（Long，内部转 String）
     * @param senderType 发送者类型（HUMAN / AI）
     * @param sessionId 会话 ID
     * @param role 消息角色（user / assistant / system）
     * @param content 消息内容
     * @return 消息信息
     */
    @Transactional
    public ChatMessageVO saveMessage(
            Long senderId, String senderType, Long sessionId, String role, String content) {
        requireConversation(sessionId);
        var msg = buildMessage(senderId, senderType, sessionId, role, content);
        messageRepository.save(msg);
        return toMessageVO(msg);
    }

    /**
     * 保存消息（含 actorType 和用户感知上下文）
     *
     * @param senderId 发送者 ID
     * @param senderType 发送者类型（HUMAN / AI）
     * @param sessionId 会话 ID
     * @param role 消息角色
     * @param content 消息内容
     * @param actorType 行动者类型（human / ai / bot / system），暂存至 awarenessContext 前缀
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
        requireConversation(sessionId);
        var msg = buildMessage(senderId, senderType, sessionId, role, content);
        msg.setAwarenessContext(awarenessContext);
        messageRepository.save(msg);
        return toMessageVO(msg);
    }

    /**
     * 保存消息（含 Token 计数和元数据）
     *
     * @param senderId 发送者 ID
     * @param senderType 发送者类型
     * @param sessionId 会话 ID
     * @param role 消息角色
     * @param content 消息内容
     * @param tokenCount Token 消耗数
     * @param metadata 元数据（JSON）
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
        requireConversation(sessionId);
        var msg = buildMessage(senderId, senderType, sessionId, role, content);
        msg.setTokenCount(tokenCount);
        msg.setMetadata(metadata);
        messageRepository.save(msg);
        return toMessageVO(msg);
    }

    /**
     * 删除会话（软删除）
     *
     * @param sessionId 会话 ID
     */
    @Transactional
    public void deleteSession(Long sessionId) {
        requireConversation(sessionId);
        conversationRepository.deleteById(sessionId);
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
        var conv = requireConversation(sessionId);
        conv.setTitle(title);
        conversationRepository.save(conv);
        return toSessionVO(conv);
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
        var msg =
                messageRepository
                        .findById(messageId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCodeConstants.CHAT_MESSAGE_NOT_FOUND));
        var feedback =
                "{\"feedback\":\"%s\",\"comment\":\"%s\"}"
                        .formatted(feedbackType, comment != null ? comment : "");
        msg.setMetadata(feedback);
        messageRepository.save(msg);
    }

    /**
     * 累加会话 Token 用量
     *
     * @param sessionId 会话 ID
     * @param tokens 本次消耗的 Token 数
     */
    @Transactional
    public void addSessionTokens(Long sessionId, long tokens) {
        var conv = requireConversation(sessionId);
        var current = conv.getTotalTokens() != null ? conv.getTotalTokens() : 0L;
        conv.setTotalTokens(current + tokens);
        conversationRepository.save(conv);
    }

    // ── 私有辅助 ──────────────────────────────────────────────────────────────

    private Conversation requireConversation(Long sessionId) {
        return conversationRepository
                .findById(sessionId)
                .orElseThrow(
                        () -> new BusinessException(ErrorCodeConstants.CHAT_SESSION_NOT_FOUND));
    }

    private ConversationMessage buildMessage(
            Long senderId, String senderType, Long sessionId, String role, String content) {
        var msg = new ConversationMessage();
        msg.setConversationId(sessionId);
        // senderId 从 Long 转 String
        msg.setSenderId(senderId != null ? senderId.toString() : "0");
        msg.setSenderType(parseSenderType(senderType));
        msg.setRole(role);
        msg.setContent(content);
        return msg;
    }

    /** 将字符串 senderType 转换为枚举，未知值降级为 HUMAN */
    private MessageSenderTypeEnum parseSenderType(String senderType) {
        if (senderType == null) return MessageSenderTypeEnum.HUMAN;
        return switch (senderType.toUpperCase()) {
            case "AI", "ASSISTANT" -> MessageSenderTypeEnum.ASSISTANT;
            case "STAFF" -> MessageSenderTypeEnum.STAFF;
            case "BOT" -> MessageSenderTypeEnum.BOT;
            case "SYSTEM" -> MessageSenderTypeEnum.SYSTEM;
            default -> MessageSenderTypeEnum.HUMAN;
        };
    }

    /** 将字符串 type 转换为枚举，未知值降级为 AI */
    private ConversationTypeEnum parseType(String type) {
        if (type == null) return ConversationTypeEnum.AI;
        try {
            return ConversationTypeEnum.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ConversationTypeEnum.AI;
        }
    }

    private ChatSessionVO toSessionVO(Conversation conv) {
        return new ChatSessionVO(
                conv.getId(),
                conv.getTitle(),
                conv.getType() != null ? conv.getType().name() : null,
                conv.getStatus() != null ? conv.getStatus().name() : null,
                conv.getCreatorId(),
                conv.getThreadId(),
                conv.getCreateTime(),
                conv.getUpdateTime());
    }

    private ChatMessageVO toMessageVO(ConversationMessage m) {
        // senderId 从 String 解析回 Long（兼容 ChatMessageVO 字段类型）
        Long senderIdLong = null;
        try {
            if (m.getSenderId() != null) senderIdLong = Long.parseLong(m.getSenderId());
        } catch (NumberFormatException ignored) {
        }
        return new ChatMessageVO(
                m.getId(),
                m.getConversationId(),
                senderIdLong,
                m.getSenderType() != null ? m.getSenderType().name() : null,
                m.getRole(),
                m.getContent(),
                m.getCreateTime());
    }
}
