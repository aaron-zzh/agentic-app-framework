package com.xuejiai.aaf.module.livechat.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.module.chat.conversation.domain.Conversation;
import com.xuejiai.aaf.module.chat.conversation.repository.ConversationRepository;
import com.xuejiai.aaf.module.chat.enums.ConversationStatus;
import com.xuejiai.aaf.module.chat.enums.ConversationType;
import com.xuejiai.aaf.module.chat.enums.MessageContentType;
import com.xuejiai.aaf.module.chat.enums.MessageSenderType;
import com.xuejiai.aaf.module.chat.message.domain.ConversationMessage;
import com.xuejiai.aaf.module.chat.message.repository.ConversationMessageRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 客服会话核心服务。
 *
 * <p>管理会话生命周期、消息存储、转接协作。迁移后使用 chat 模块的 Conversation / ConversationMessage。
 *
 * @author AaronZZH & Kiro
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSessionService {

    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository messageRepository;
    private final SeatService seatService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 获取或创建会话（用户发消息时调用）。
     *
     * <p>以 creatorId 标识访客，渠道信息记录在 channelExtension。
     *
     * @param creatorId 访客 ID（externalUserId 对应 creatorId）
     * @param channelType 渠道类型（存入 channelExtension，此处保留参数兼容调用方）
     * @return 已有活跃会话或新建会话
     */
    @Transactional
    public Conversation getOrCreateSession(Long creatorId, String channelType) {
        // 查找该访客未关闭的客服会话
        return conversationRepository
                .findActiveByCreatorIdAndType(creatorId, ConversationType.LIVECHAT)
                .orElseGet(
                        () -> {
                            var conv = new Conversation();
                            conv.setType(ConversationType.LIVECHAT);
                            conv.setStatus(ConversationStatus.BOT);
                            conv.setCreatorId(creatorId);
                            conv.setPriority(3);
                            // 渠道类型存入 channelExtension JSON
                            conv.setChannelExtension("{\"channel_type\":\"" + channelType + "\"}");
                            return conversationRepository.save(conv);
                        });
    }

    /**
     * 保存消息。
     *
     * @param conversationId 会话 ID
     * @param senderType 发送方类型
     * @param senderId 发送方 ID（String，兼容 userId/agentId/staffId）
     * @param content 消息内容
     * @param internal 是否内部消息
     * @return 保存后的消息
     */
    @Transactional
    public ConversationMessage saveMessage(
            Long conversationId,
            MessageSenderType senderType,
            String senderId,
            String content,
            boolean internal) {
        var msg = new ConversationMessage();
        msg.setConversationId(conversationId);
        msg.setSenderType(senderType);
        msg.setSenderId(senderId);
        msg.setContentType(MessageContentType.TEXT);
        msg.setContent(content);
        msg.setIsInternal(internal);
        // 设置 LLM role
        msg.setRole(senderType == MessageSenderType.HUMAN ? "user" : "assistant");
        // 更新会话活跃时间（closedAt 置空表示仍活跃）
        conversationRepository
                .findById(conversationId)
                .ifPresent(c -> conversationRepository.save(c));
        return messageRepository.save(msg);
    }

    /**
     * 转人工——将会话状态改为 WAITING，并尝试自动分配坐席。
     *
     * @param conversationId 会话 ID
     * @param skillGroup 技能组（可为 null）
     */
    @Transactional
    public void transferToHuman(Long conversationId, String skillGroup) {
        var conv = findById(conversationId);
        conv.setStatus(ConversationStatus.WAITING);
        if (skillGroup != null) {
            // 将技能组信息合并写入 channelExtension
            String ext = conv.getChannelExtension();
            if (ext == null) ext = "{}";
            // 简单追加 skill_group 字段（JSON 拼接）
            ext =
                    ext.substring(0, ext.length() - 1)
                            + (ext.length() > 2 ? "," : "")
                            + "\"skill_group\":\""
                            + skillGroup
                            + "\"}";
            conv.setChannelExtension(ext);
        }
        conversationRepository.save(conv);
        // 尝试自动分配坐席
        seatService
                .allocate(conv)
                .ifPresent(seat -> seatService.acceptSession(seat.getId(), conversationId));
        log.info("会话转人工: conversationId={}, skillGroup={}", conversationId, skillGroup);
    }

    /**
     * 会话转接。
     *
     * @param conversationId 会话 ID
     * @param fromStaffId 原坐席 ID
     * @param toStaffId 目标坐席 ID（与 toSkillGroup 二选一）
     * @param toSkillGroup 目标技能组
     * @param reason 转接原因
     * @param note 备注
     */
    @Transactional
    public void transfer(
            Long conversationId,
            Long fromStaffId,
            Long toStaffId,
            String toSkillGroup,
            String reason,
            String note) {
        var conv = findById(conversationId);
        // 转接信息记录为内部消息
        String transferNote =
                String.format(
                        "会话转接：from=%d, to=%s, reason=%s, note=%s",
                        fromStaffId, toStaffId != null ? toStaffId : toSkillGroup, reason, note);
        saveMessage(conversationId, MessageSenderType.SYSTEM, "system", transferNote, true);
        // 释放原坐席
        seatService.releaseSession(fromStaffId);
        // 分配新坐席
        if (toStaffId != null) {
            conv.setStaffId(toStaffId);
            conv.setStatus(ConversationStatus.ACTIVE);
            conversationRepository.save(conv);
        } else {
            // 转入技能组待分配
            transferToHuman(conversationId, toSkillGroup);
        }
        log.info("会话转接: conversationId={}, from={}, to={}", conversationId, fromStaffId, toStaffId);
    }

    /**
     * 邀请坐席协作（发送内部消息）。
     *
     * @param conversationId 会话 ID
     * @param staffId 协作坐席 ID
     * @param message 内部消息内容
     * @return 保存后的消息
     */
    @Transactional
    public ConversationMessage inviteCollaborate(
            Long conversationId, Long staffId, String message) {
        return saveMessage(
                conversationId, MessageSenderType.STAFF, String.valueOf(staffId), message, true);
    }

    /**
     * 关闭会话。
     *
     * @param conversationId 会话 ID
     */
    @Transactional
    public void closeSession(Long conversationId) {
        var conv = findById(conversationId);
        if (conv.getStaffId() != null) {
            seatService.releaseSession(conv.getStaffId());
        }
        conv.setStatus(ConversationStatus.CLOSED);
        conv.setClosedAt(LocalDateTime.now());
        conversationRepository.save(conv);
        // 触发评价邀请事件
        eventPublisher.publishEvent(new SessionClosedEvent(conv));
    }

    /**
     * 获取等待人工接入的会话列表。
     *
     * @return 按优先级和创建时间排序的等待列表
     */
    public List<Conversation> getWaitingList() {
        return conversationRepository.findByStatusAndTypeOrderByPriorityDescCreateTimeAsc(
                ConversationStatus.WAITING, ConversationType.LIVECHAT);
    }

    /**
     * 获取坐席当前服务的会话列表。
     *
     * @param staffId 坐席 ID
     * @return 进行中的会话列表
     */
    public List<Conversation> getStaffSessions(Long staffId) {
        return conversationRepository.findByStaffIdAndStatus(staffId, ConversationStatus.ACTIVE);
    }

    /**
     * 获取会话消息（用户视角，不含内部消息）。
     *
     * @param conversationId 会话 ID
     * @return 消息列表
     */
    public List<ConversationMessage> getMessages(Long conversationId) {
        return messageRepository.findByConversationIdAndIsInternalFalseOrderByCreateTimeAsc(
                conversationId);
    }

    /**
     * 获取会话消息（坐席视角，含内部消息）。
     *
     * @param conversationId 会话 ID
     * @return 消息列表
     */
    public List<ConversationMessage> getMessagesForStaff(Long conversationId) {
        return messageRepository.findByConversationIdOrderByCreateTimeAsc(conversationId);
    }

    /**
     * 超时处理：活跃会话无响应自动关闭。
     *
     * @param timeoutMinutes 超时分钟数
     */
    @Transactional
    public void closeInactiveSessions(int timeoutMinutes) {
        var threshold = LocalDateTime.now().minusMinutes(timeoutMinutes);
        var sessions =
                conversationRepository.findByStatusAndTypeAndUpdateTimeBefore(
                        ConversationStatus.ACTIVE, ConversationType.LIVECHAT, threshold);
        sessions.forEach(s -> closeSession(s.getId()));
        log.info("关闭超时会话: count={}", sessions.size());
    }

    /**
     * 超时处理：等待中会话重新分配坐席。
     *
     * @param timeoutMinutes 超时分钟数
     */
    @Transactional
    public void reassignWaitingSessions(int timeoutMinutes) {
        var threshold = LocalDateTime.now().minusMinutes(timeoutMinutes);
        var sessions =
                conversationRepository.findByStatusAndTypeAndUpdateTimeBefore(
                        ConversationStatus.WAITING, ConversationType.LIVECHAT, threshold);
        sessions.forEach(
                s ->
                        seatService
                                .allocate(s)
                                .ifPresent(
                                        seat ->
                                                seatService.acceptSession(
                                                        seat.getId(), s.getId())));
    }

    private Conversation findById(Long id) {
        return conversationRepository
                .findById(id)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "会话不存在"));
    }
}
