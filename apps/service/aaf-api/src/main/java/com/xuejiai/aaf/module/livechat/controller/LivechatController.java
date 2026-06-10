package com.xuejiai.aaf.module.livechat.controller;

import java.util.ArrayList;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.enums.livechat.SessionStatusEnum;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.chat.conversation.domain.Conversation;
import com.xuejiai.aaf.module.chat.enums.ConversationStatus;
import com.xuejiai.aaf.module.chat.enums.MessageSenderType;
import com.xuejiai.aaf.module.chat.message.domain.ConversationMessage;
import com.xuejiai.aaf.module.knowledge.service.KnowledgeSegmentService;
import com.xuejiai.aaf.module.knowledge.vo.SemanticSearchResultVO;
import com.xuejiai.aaf.module.livechat.service.ChatSessionService;
import com.xuejiai.aaf.module.livechat.service.SeatService;
import com.xuejiai.aaf.module.livechat.vo.ChatMessageVO;
import com.xuejiai.aaf.module.livechat.vo.ChatSessionVO;
import com.xuejiai.aaf.module.livechat.vo.SessionTransferDTO;
import com.xuejiai.aaf.module.livechat.vo.StaffSendMessageDTO;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 客服工作台 API。
 *
 * <p>已适配迁移后的 ChatSessionService（使用 Conversation / ConversationMessage）。 后续 livechat 工作台功能统一迁移至
 * /api/chat/livechat/* 路径，本接口最终删除。
 */
@Tag(name = "Livechat - 客服工作台")
@RestController
@RequestMapping("/api/livechat")
@RequiredArgsConstructor
public class LivechatController {

    private final ChatSessionService sessionService;
    private final SeatService seatService;
    private final KnowledgeSegmentService segmentService;

    /** 待接入列表 */
    @GetMapping("/sessions/waiting")
    public Result<List<ChatSessionVO>> waitingList() {
        return Result.success(sessionService.getWaitingList().stream().map(this::toVO).toList());
    }

    /** 坐席当前会话列表 */
    @GetMapping("/sessions/mine")
    public Result<List<ChatSessionVO>> mySessions(@RequestParam Long staffId) {
        return Result.success(
                sessionService.getStaffSessions(staffId).stream().map(this::toVO).toList());
    }

    /** 接入会话 */
    @PostMapping("/sessions/{sessionId}/accept")
    public Result<Void> acceptSession(@PathVariable Long sessionId, @RequestParam Long seatId) {
        seatService.acceptSession(seatId, sessionId);
        return Result.success(null);
    }

    /** 获取会话消息（坐席视角，含内部消息） */
    @GetMapping("/sessions/{sessionId}/messages")
    public Result<List<ChatMessageVO>> getMessages(@PathVariable Long sessionId) {
        List<ConversationMessage> rawList = sessionService.getMessagesForStaff(sessionId);
        List<ChatMessageVO> result = new ArrayList<>(rawList.size());
        for (ConversationMessage m : rawList) {
            result.add(
                    new ChatMessageVO(
                            m.getId(),
                            m.getConversationId(),
                            m.getSenderType(),
                            m.getSenderId(),
                            m.getContentType(),
                            m.getContent(),
                            m.getIsInternal(),
                            m.getCreateTime()));
        }
        return Result.success(result);
    }

    /** 坐席发送消息 */
    @PostMapping("/messages/send")
    public Result<Void> sendMessage(
            @RequestParam Long staffId, @RequestBody @Valid StaffSendMessageDTO dto) {
        sessionService.saveMessage(
                dto.sessionId(),
                MessageSenderType.STAFF,
                String.valueOf(staffId),
                dto.content(),
                dto.internal());
        return Result.success(null);
    }

    /** 会话转接 */
    @PostMapping("/sessions/{sessionId}/transfer")
    public Result<Void> transfer(
            @PathVariable Long sessionId,
            @RequestParam Long fromStaffId,
            @RequestBody @Valid SessionTransferDTO dto) {
        sessionService.transfer(
                sessionId,
                fromStaffId,
                dto.toStaffId(),
                dto.toSkillGroup(),
                dto.reason() != null ? dto.reason().name() : null,
                dto.note());
        return Result.success(null);
    }

    /** 邀请协作 */
    @PostMapping("/sessions/{sessionId}/collaborate")
    public Result<Void> collaborate(
            @PathVariable Long sessionId, @RequestParam Long staffId, @RequestBody String message) {
        sessionService.inviteCollaborate(sessionId, staffId, message);
        return Result.success(null);
    }

    /** 关闭会话 */
    @PostMapping("/sessions/{sessionId}/close")
    public Result<Void> closeSession(@PathVariable Long sessionId) {
        sessionService.closeSession(sessionId);
        return Result.success(null);
    }

    /** 坐席上线 */
    @PostMapping("/seat/online")
    public Result<Void> goOnline(@RequestParam Long userId) {
        seatService.goOnline(userId);
        return Result.success(null);
    }

    /** 坐席离线 */
    @PostMapping("/seat/offline")
    public Result<Void> goOffline(@RequestParam Long userId) {
        seatService.goOffline(userId);
        return Result.success(null);
    }

    /** 知识库搜索（坐席辅助） */
    @GetMapping("/knowledge/search")
    public Result<List<SemanticSearchResultVO>> searchKnowledge(
            @RequestParam Long knowledgeBaseId,
            @RequestParam String query,
            @RequestParam(defaultValue = "5") Integer topK) {
        return Result.success(segmentService.semanticSearch(knowledgeBaseId, query, topK));
    }

    /** Conversation → ChatSessionVO（渠道字段存于 channelExtension，此处仅映射通用字段） */
    private ChatSessionVO toVO(Conversation c) {
        SessionStatusEnum status = c.getStatus() != null ? mapStatus(c.getStatus()) : null;
        return new ChatSessionVO(
                c.getId(),
                null, // externalUserId 存于 channelExtension，暂不解析
                null, // channelType 存于 channelExtension，暂不解析
                status,
                c.getStaffId(),
                null, // skillGroup 存于 channelExtension
                null, // tags 存于 channelExtension
                c.getPriority(),
                c.getUpdateTime(), // Conversation 无 lastActiveTime，用 updateTime 代替
                c.getCreateTime());
    }

    /** ConversationStatus → SessionStatusEnum 映射 */
    private SessionStatusEnum mapStatus(ConversationStatus s) {
        return switch (s) {
            case ACTIVE -> SessionStatusEnum.ACTIVE;
            case WAITING -> SessionStatusEnum.WAITING;
            case BOT -> SessionStatusEnum.BOT;
            case CLOSED -> SessionStatusEnum.CLOSED;
            default -> null;
        };
    }
}
