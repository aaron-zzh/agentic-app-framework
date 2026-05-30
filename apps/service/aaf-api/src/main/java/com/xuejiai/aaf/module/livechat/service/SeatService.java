package com.xuejiai.aaf.module.livechat.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.enums.livechat.SeatStatusEnum;
import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.module.livechat.domain.ChatSession;
import com.xuejiai.aaf.module.livechat.domain.LivechatSeat;
import com.xuejiai.aaf.module.livechat.repository.LivechatChatSessionRepository;
import com.xuejiai.aaf.module.livechat.repository.LivechatSeatRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 坐席管理服务。
 *
 * <p>负责坐席分配、状态管理、会话接管。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeatService {

    private final LivechatSeatRepository seatRepository;
    private final LivechatChatSessionRepository sessionRepository;

    /**
     * 分配坐席——按技能组/空闲度分配。
     *
     * @param session 待分配的会话
     * @return 分配到的坐席，空表示无可用坐席
     */
    public Optional<LivechatSeat> allocate(ChatSession session) {
        // 优先按技能组匹配
        if (session.getSkillGroup() != null) {
            var seats = seatRepository.findAvailableBySkillGroup(session.getSkillGroup());
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
     */
    @Transactional
    public void acceptSession(Long seatId, Long sessionId) {
        var seat = findSeatById(seatId);
        if (!seat.hasCapacity()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "坐席已达最大并发数");
        }
        var session = findSessionById(sessionId);
        session.assignStaff(seat.getUserId());
        seat.incrementSessions();
        sessionRepository.save(session);
        seatRepository.save(seat);
        log.info("坐席接入会话: seatId={}, sessionId={}", seatId, sessionId);
    }

    /**
     * 坐席上线。
     */
    @Transactional
    public void goOnline(Long userId) {
        var seat = seatRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "坐席不存在"));
        seat.setStatus(SeatStatusEnum.ONLINE);
        seatRepository.save(seat);
    }

    /**
     * 坐席离线。
     */
    @Transactional
    public void goOffline(Long userId) {
        var seat = seatRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "坐席不存在"));
        seat.setStatus(SeatStatusEnum.OFFLINE);
        seatRepository.save(seat);
    }

    /**
     * 释放会话（会话关闭时调用）。
     */
    @Transactional
    public void releaseSession(Long staffId) {
        seatRepository.findByUserId(staffId).ifPresent(seat -> {
            seat.decrementSessions();
            seatRepository.save(seat);
        });
    }

    private LivechatSeat findSeatById(Long id) {
        return seatRepository.findById(id)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "坐席不存在"));
    }

    private ChatSession findSessionById(Long id) {
        return sessionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "会话不存在"));
    }
}
