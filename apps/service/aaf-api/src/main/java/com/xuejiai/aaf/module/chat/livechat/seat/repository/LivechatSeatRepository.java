package com.xuejiai.aaf.module.chat.livechat.seat.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import com.xuejiai.aaf.common.enums.chat.SeatTypeEnum;
import com.xuejiai.aaf.module.chat.livechat.seat.domain.LivechatSeat;

/**
 * 客服坐席数据访问层。
 *
 * @author AaronZZH & Kiro
 */
public interface LivechatSeatRepository
        extends JpaRepository<LivechatSeat, Long>, JpaSpecificationExecutor<LivechatSeat> {

    Optional<LivechatSeat> findByUserId(Long userId);

    Optional<LivechatSeat> findByAssistantId(Long assistantId);

    List<LivechatSeat> findBySeatTypeAndStatus(SeatTypeEnum seatType, String status);

    // ========== 坐席分配场景查询 ==========

    /** 按技能组查询可接待坐席（在线且未满载） */
    @Query(
            "SELECT s FROM LivechatSeat s WHERE s.skillGroup = :skillGroup AND s.status = 'online' AND s.currentSessions < s.maxSessions AND s.deleted = false ORDER BY s.currentSessions ASC")
    List<LivechatSeat> findAvailableBySkillGroup(String skillGroup);

    /** 查询所有可接待坐席（在线且未满载） */
    @Query(
            "SELECT s FROM LivechatSeat s WHERE s.status = 'online' AND s.currentSessions < s.maxSessions AND s.deleted = false ORDER BY s.currentSessions ASC")
    List<LivechatSeat> findAllAvailable();
}
