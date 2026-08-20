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
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.security.authorization.RelationPermissionWriter;
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

    /**
     * m14：非人类发送者（AI / SYSTEM / BOT）的 senderId 占位值。
     *
     * <p>{@code sender_id} 列 NOT NULL 且为字符串，AI 消息本身没有用户主键。原代码在多处直接写死 {@code 0L}/{@code "0"} 表示"AI
     * 发的"，语义只能靠约定传递；这里收敛为具名常量， 真正的行动者维度由 {@code senderType}（{@link MessageSenderTypeEnum}）表达。
     */
    public static final String NON_HUMAN_SENDER_ID = "0";

    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository messageRepository;
    private final RelationPermissionWriter relationPermissionWriter;
    private final com.xuejiai.aaf.framework.security.OperatorContext operatorContext;

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
        relationPermissionWriter.grant(userId, "session", String.valueOf(conv.getId()), "OWNER");
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
        // M18：先校验会话归属，避免任意登录用户按 sessionId 读他人消息
        requireOwnedConversation(sessionId);
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
     * 校验当前用户拥有可作为 AG-UI 运行边界的 AI 会话 thread。
     *
     * <p>AG-UI 运行的 conversationId/sessionId 固定等于 threadId；因此必须在创建持久任务和 lease 前 拒绝其他用户的
     * thread，避免跨用户共享同一租约或任务队列。
     */
    @Transactional(readOnly = true)
    public void requireOwnedAiThread(String threadId) {
        if (threadId == null || threadId.isBlank()) {
            throw new BusinessException(ErrorCodeConstants.CHAT_SESSION_NOT_FOUND);
        }
        var conversation =
                conversationRepository
                        .findByThreadId(threadId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCodeConstants.CHAT_SESSION_NOT_FOUND));
        if (conversation.getType() != ConversationTypeEnum.AI) {
            throw new BusinessException(ErrorCodeConstants.CHAT_SESSION_NOT_FOUND);
        }
        requireOwnedConversation(conversation.getId());
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
        // M18：分页读取同样需要归属校验
        requireOwnedConversation(sessionId);
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
        // M18：归档属于会话管理操作，须校验归属
        var conv = requireOwnedConversation(sessionId);
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
     * 用户主动发消息（REST 入口专用）。
     *
     * <p>M18：与内部/AI 写入路径（{@code saveMessage} 各重载，由事件监听器和 AG-UI 链路调用，运行时无 SecurityContext）
     * 区分开——此方法要求 sessionId 归属当前身份，避免用户把消息写进他人会话。
     */
    @Transactional
    public ChatMessageVO saveUserMessage(Long senderId, Long sessionId, String content) {
        requireOwnedConversation(sessionId);
        var msg = buildMessage(senderId, "HUMAN", sessionId, "user", content);
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
        // M18：删除他人会话是最高危的 IDOR 面，须校验归属
        requireOwnedConversation(sessionId);
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
        // M18：重命名属于会话管理操作，须校验归属
        var conv = requireOwnedConversation(sessionId);
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
        // M18：messageId 来自路径，须经所属会话反查归属，否则可对他人消息打标
        requireOwnedConversation(msg.getConversationId());
        // m13：反馈内容用 Jackson 构造 JSON——手工拼串遇到引号/换行/反斜杠会产出非法 JSON
        var node = JsonUtils.createObjectNode();
        node.put("feedback", feedbackType);
        node.put("comment", comment != null ? comment : "");
        msg.setMetadata(node.toString());
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

    /**
     * M18：校验会话归属当前用户，防对象级越权（IDOR）。
     *
     * <p>sessionId/messageId 由客户端从路径传入，只有存在性校验时任何登录用户都能读/删/改他人会话。 这里统一比对 {@code
     * conversation.creatorId} 与当前身份；不属于自己时抛"会话不存在"而非 403， 避免通过错误码枚举出他人会话是否存在。
     */
    private Conversation requireOwnedConversation(Long sessionId) {
        var conv = requireConversation(sessionId);
        var currentUserId =
                operatorContext
                        .currentOwnerId()
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCodeConstants.CHAT_SESSION_NOT_FOUND));
        if (!java.util.Objects.equals(conv.getCreatorId(), currentUserId)) {
            throw new BusinessException(ErrorCodeConstants.CHAT_SESSION_NOT_FOUND);
        }
        return conv;
    }

    private ConversationMessage buildMessage(
            Long senderId, String senderType, Long sessionId, String role, String content) {
        var msg = new ConversationMessage();
        msg.setConversationId(sessionId);
        // m14：人类发送者存用户 ID；AI/系统等非人类发送者用具名占位值，行动者语义由 senderType 承载
        msg.setSenderId(senderId != null ? senderId.toString() : NON_HUMAN_SENDER_ID);
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
