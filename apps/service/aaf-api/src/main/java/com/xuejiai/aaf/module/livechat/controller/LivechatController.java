package com.xuejiai.aaf.module.livechat.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.enums.livechat.SenderTypeEnum;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.channel.domain.UnifiedMessage;
import com.xuejiai.aaf.module.channel.service.ChannelMessageRouter;
import com.xuejiai.aaf.module.knowledge.service.KnowledgeSegmentService;
import com.xuejiai.aaf.module.knowledge.vo.SemanticSearchResultVO;
import com.xuejiai.aaf.module.livechat.domain.ChatSession;
import com.xuejiai.aaf.module.livechat.service.ChatSessionService;
import com.xuejiai.aaf.module.livechat.service.SeatService;
import com.xuejiai.aaf.module.livechat.vo.ChatMessageVO;
import com.xuejiai.aaf.module.livechat.vo.ChatSessionVO;
import com.xuejiai.aaf.module.livechat.vo.SessionTransferDTO;
import com.xuejiai.aaf.module.livechat.vo.StaffSendMessageDTO;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 客服工作台 API。
 */
@RestController
@RequestMapping("/api/livechat")
@RequiredArgsConstructor
public class LivechatController {

    private final ChatSessionService sessionService;
    private final SeatService seatService;
    private final ChannelMessageRouter channelRouter;
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
        return Result.success(
                sessionService.getMessagesForStaff(sessionId).stream()
                        .map(m -> new ChatMessageVO(
                                m.getId(), m.getSessionId(), m.getSenderType(), m.getSenderId(),
                                m.getMessageType(), m.getContent(), m.getInternal(),
                                m.getCreateTime()))
                        .toList());
    }

    /** 坐席发送消息 */
    @PostMapping("/messages/send")
    public Result<Void> sendMessage(
            @RequestParam Long staffId, @RequestBody @Valid StaffSendMessageDTO dto) {
        // 存储坐席消息
        sessionService.saveMessage(
                dto.sessionId(), SenderTypeEnum.STAFF, staffId, dto.content(), dto.internal());
        // 非内部消息需推送给用户（通过渠道路由）
        if (!dto.internal()) {
            sessionService.getStaffSessions(staffId).stream()
                    .filter(s -> s.getId().equals(dto.sessionId()))
                    .findFirst()
                    .ifPresent(s -> {
                        var reply = UnifiedMessage.outboundText(
                                s.getChannelType(), s.getExternalUserId(), dto.content());
                        channelRouter.routeOutbound(reply);
                    });
        }
        return Result.success(null);
    }

    /** 会话转接 */
    @PostMapping("/sessions/{sessionId}/transfer")
    public Result<Void> transfer(
            @PathVariable Long sessionId,
            @RequestParam Long fromStaffId,
            @RequestBody @Valid SessionTransferDTO dto) {
        sessionService.transfer(
                sessionId, fromStaffId, dto.toStaffId(),
                dto.toSkillGroup(), dto.reason(), dto.note());
        return Result.success(null);
    }

    /** 邀请协作 */
    @PostMapping("/sessions/{sessionId}/collaborate")
    public Result<Void> collaborate(
            @PathVariable Long sessionId,
            @RequestParam Long staffId,
            @RequestBody String message) {
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

    private ChatSessionVO toVO(ChatSession s) {
        return new ChatSessionVO(
                s.getId(), s.getExternalUserId(), s.getChannelType(), s.getStatus(),
                s.getStaffId(), s.getSkillGroup(), s.getTags(), s.getPriority(),
                s.getLastActiveTime(), s.getCreateTime());
    }
}
