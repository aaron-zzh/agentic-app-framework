package com.xuejiai.aaf.module.livechat.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.module.chat.conversation.domain.Conversation;
import com.xuejiai.aaf.module.chat.conversation.repository.ConversationRepository;
import com.xuejiai.aaf.module.chat.enums.ConversationStatus;
import com.xuejiai.aaf.module.chat.livechat.seat.domain.LivechatSeat;
import com.xuejiai.aaf.module.chat.livechat.seat.repository.LivechatSeatRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 坐席管理服务。
 *
 * <p>负责坐席分配、状态管理、会话接管。迁移后使用 chat 模块的 LivechatSeat / Conversation。
 *
 * @author AaronZZH & Kiro
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeatService {

    private final LivechatSeatRepository seatRepository;
    private final ConversationRepository conversationRepository;

    /**
     * 分配坐席——按技能组/空闲度分配。
     *
     * @param conversation 待分配的会话
     * @return 分配到的坐席，空表示无可用坐席
     */
    public Optional<LivechatSeat> allocate(Conversation conversation) {
        // 从 channelExtension JSON 中读取技能组（简单字符串解析）
        String skillGroup = extractSkillGroup(conversation.getChannelExtension());
        if (skillGroup != null) {
            var seats = seatRepository.findAvailableBySkillGroup(skillGroup);
            if (!seats.isEmpty()) {
                return Optional.of(seats.getFirst());
            }
        }
        // 兜底：分配任意空闲坐席
        var allAvailable = seatRepository.findAllAvailable();
        return allAvailable.isEmpty() ? Optional.empty() : Optional.of(allAvailable.getFirst());
    }

    /**
     * 坐席接入会话。
     *
     * @param seatId 坐席 ID
     * @param conversationId 会话 ID
     */
    @Transactional
    public void acceptSession(Long seatId, Long conversationId) {
        var seat = findSeatById(seatId);
        if (!seat.hasCapacity()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "坐席已达最大并发数");
        }
        var conv = findConversationById(conversationId);
        // 更新会话：分配坐席，状态改为 ACTIVE
        conv.setStaffId(seat.getUserId());
        conv.setStatus(ConversationStatus.ACTIVE);
        seat.incrementSessions();
        conversationRepository.save(conv);
        seatRepository.save(seat);
        log.info("坐席接入会话: seatId={}, conversationId={}", seatId, conversationId);
    }

    /**
     * 坐席上线。
     *
     * @param userId 坐席关联的用户 ID
     */
    @Transactional
    public void goOnline(Long userId) {
        var seat =
                seatRepository
                        .findByUserId(userId)
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "坐席不存在"));
        seat.setStatus("online");
        seatRepository.save(seat);
    }

    /**
     * 坐席离线。
     *
     * @param userId 坐席关联的用户 ID
     */
    @Transactional
    public void goOffline(Long userId) {
        var seat =
                seatRepository
                        .findByUserId(userId)
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "坐席不存在"));
        seat.setStatus("offline");
        seatRepository.save(seat);
    }

    /**
     * 释放会话（会话关闭时调用，减少坐席当前会话计数）。
     *
     * @param staffId 坐席关联的用户 ID
     */
    @Transactional
    public void releaseSession(Long staffId) {
        seatRepository
                .findByUserId(staffId)
                .ifPresent(
                        seat -> {
                            seat.decrementSessions();
                            seatRepository.save(seat);
                        });
    }

    private LivechatSeat findSeatById(Long id) {
        return seatRepository
                .findById(id)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "坐席不存在"));
    }

    private Conversation findConversationById(Long id) {
        return conversationRepository
                .findById(id)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "会话不存在"));
    }

    /**
     * 从 channelExtension JSON 中提取 skill_group 值。
     *
     * <p>简单字符串解析，避免引入 JSON 库依赖。
     */
    private String extractSkillGroup(String channelExtension) {
        if (channelExtension == null || !channelExtension.contains("skill_group")) {
            return null;
        }
        // 匹配 "skill_group":"value"
        int idx = channelExtension.indexOf("\"skill_group\"");
        if (idx < 0) return null;
        int colon = channelExtension.indexOf(':', idx);
        if (colon < 0) return null;
        int start = channelExtension.indexOf('"', colon + 1);
        if (start < 0) return null;
        int end = channelExtension.indexOf('"', start + 1);
        if (end < 0) return null;
        String val = channelExtension.substring(start + 1, end);
        return val.isBlank() ? null : val;
    }
}
