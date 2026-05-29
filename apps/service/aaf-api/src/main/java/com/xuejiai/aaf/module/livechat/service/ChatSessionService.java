package com.xuejiai.aaf.module.livechat.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.enums.channel.ChannelTypeEnum;
import com.xuejiai.aaf.common.enums.channel.MessageTypeEnum;
import com.xuejiai.aaf.common.enums.livechat.SenderTypeEnum;
import com.xuejiai.aaf.common.enums.livechat.SessionStatusEnum;
import com.xuejiai.aaf.common.enums.livechat.TransferReasonEnum;
import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.module.livechat.domain.ChatMessage;
import com.xuejiai.aaf.module.livechat.domain.ChatSession;
import com.xuejiai.aaf.module.livechat.domain.SessionTransfer;
import com.xuejiai.aaf.module.livechat.repository.ChatMessageRepository;
import com.xuejiai.aaf.module.livechat.repository.ChatSessionRepository;
import com.xuejiai.aaf.module.livechat.repository.SessionTransferRepository;

import org.springframework.context.ApplicationEventPublisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 客服会话核心服务。
 *
 * <p>管理会话生命周期、消息存储、转接协作。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSessionService {

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final SessionTransferRepository transferRepository;
    private final SeatService seatService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 获取或创建会话（用户发消息时调用）。
     */
    @Transactional
    public ChatSession getOrCreateSession(String externalUserId, ChannelTypeEnum channelType) {
        return sessionRepository
                .findByExternalUserIdAndChannelTypeAndStatusNot(
                        externalUserId, channelType, SessionStatusEnum.CLOSED)
                .orElseGet(() -> {
                    var session = new ChatSession();
                    session.setExternalUserId(externalUserId);
                    session.setChannelType(channelType);
                    session.setStatus(SessionStatusEnum.BOT);
                    session.setLastActiveTime(LocalDateTime.now());
                    session.setPriority(3);
                    return sessionRepository.save(session);
                });
    }

    /**
     * 保存消息。
     */
    @Transactional
    public ChatMessage saveMessage(Long sessionId, SenderTypeEnum senderType, Long senderId,
                                   String content, boolean internal) {
        var msg = new ChatMessage();
        msg.setSessionId(sessionId);
        msg.setSenderType(senderType);
        msg.setSenderId(senderId);
        msg.setMessageType(MessageTypeEnum.TEXT);
        msg.setContent(content);
        msg.setInternal(internal);
        // 更新会话活跃时间
        sessionRepository.findById(sessionId).ifPresent(s -> {
            s.setLastActiveTime(LocalDateTime.now());
            sessionRepository.save(s);
        });
        return messageRepository.save(msg);
    }

    /**
     * 转人工。
     */
    @Transactional
    public void transferToHuman(Long sessionId, String skillGroup) {
        var session = findById(sessionId);
        session.transferToHuman();
        if (skillGroup != null) {
            session.setSkillGroup(skillGroup);
        }
        sessionRepository.save(session);
        // 尝试自动分配坐席
        seatService.allocate(session).ifPresent(seat -> {
            seatService.acceptSession(seat.getId(), sessionId);
        });
        log.info("会话转人工: sessionId={}, skillGroup={}", sessionId, skillGroup);
    }

    /**
     * 会话转接。
     */
    @Transactional
    public void transfer(Long sessionId, Long fromStaffId, Long toStaffId,
                         String toSkillGroup, TransferReasonEnum reason, String note) {
        var session = findById(sessionId);
        // 记录转接
        var transfer = new SessionTransfer();
        transfer.setSessionId(sessionId);
        transfer.setFromStaffId(fromStaffId);
        transfer.setToStaffId(toStaffId);
        transfer.setToSkillGroup(toSkillGroup);
        transfer.setReason(reason);
        transfer.setNote(note);
        transferRepository.save(transfer);
        // 释放原坐席
        seatService.releaseSession(fromStaffId);
        // 分配新坐席
        if (toStaffId != null) {
            session.assignStaff(toStaffId);
            sessionRepository.save(session);
        } else {
            // 转入技能组待分配
            session.setSkillGroup(toSkillGroup);
            session.transferToHuman();
            sessionRepository.save(session);
            seatService.allocate(session).ifPresent(seat ->
                    seatService.acceptSession(seat.getId(), sessionId));
        }
        log.info("会话转接: sessionId={}, from={}, to={}", sessionId, fromStaffId, toStaffId);
    }

    /**
     * 邀请坐席协作（发送内部消息）。
     */
    @Transactional
    public ChatMessage inviteCollaborate(Long sessionId, Long staffId, String message) {
        return saveMessage(sessionId, SenderTypeEnum.STAFF, staffId, message, true);
    }

    /**
     * 关闭会话。
     */
    @Transactional
    public void closeSession(Long sessionId) {
        var session = findById(sessionId);
        if (session.getStaffId() != null) {
            seatService.releaseSession(session.getStaffId());
        }
        session.close();
        sessionRepository.save(session);
        // 触发评价邀请事件
        eventPublisher.publishEvent(new SessionClosedEvent(session));
    }

    /**
     * 获取待接入列表。
     */
    public List<ChatSession> getWaitingList() {
        return sessionRepository.findByStatusOrderByPriorityDescCreateTimeAsc(
                SessionStatusEnum.WAITING);
    }

    /**
     * 获取坐席当前会话列表。
     */
    public List<ChatSession> getStaffSessions(Long staffId) {
        return sessionRepository.findByStaffIdAndStatus(staffId, SessionStatusEnum.ACTIVE);
    }

    /**
     * 获取会话消息（用户视角，不含内部消息）。
     */
    public List<ChatMessage> getMessages(Long sessionId) {
        return messageRepository.findBySessionIdAndInternalFalseOrderByCreateTimeAsc(sessionId);
    }

    /**
     * 获取会话消息（坐席视角，含内部消息）。
     */
    public List<ChatMessage> getMessagesForStaff(Long sessionId) {
        return messageRepository.findBySessionIdOrderByCreateTimeAsc(sessionId);
    }

    /**
     * 超时处理：用户无响应自动关闭。
     */
    @Transactional
    public void closeInactiveSessions(int timeoutMinutes) {
        var threshold = LocalDateTime.now().minusMinutes(timeoutMinutes);
        var sessions = sessionRepository.findByStatusAndLastActiveTimeBefore(
                SessionStatusEnum.ACTIVE, threshold);
        sessions.forEach(s -> closeSession(s.getId()));
        log.info("关闭超时会话: count={}", sessions.size());
    }

    /**
     * 超时处理：等待中会话重新分配。
     */
    @Transactional
    public void reassignWaitingSessions(int timeoutMinutes) {
        var threshold = LocalDateTime.now().minusMinutes(timeoutMinutes);
        var sessions = sessionRepository.findByStatusAndLastActiveTimeBefore(
                SessionStatusEnum.WAITING, threshold);
        sessions.forEach(s -> seatService.allocate(s).ifPresent(seat ->
                seatService.acceptSession(seat.getId(), s.getId())));
    }

    private ChatSession findById(Long id) {
        return sessionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "会话不存在"));
    }
}
