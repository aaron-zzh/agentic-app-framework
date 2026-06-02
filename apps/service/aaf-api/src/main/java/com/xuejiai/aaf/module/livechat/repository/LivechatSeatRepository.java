package com.xuejiai.aaf.module.livechat.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.xuejiai.aaf.common.enums.livechat.SeatStatusEnum;
import com.xuejiai.aaf.module.livechat.domain.LivechatSeat;

public interface LivechatSeatRepository extends JpaRepository<LivechatSeat, Long> {

    Optional<LivechatSeat> findByUserId(Long userId);

    /** 按技能组查找有空闲容量的坐席，按当前会话数升序（优先分配空闲的） */
    @Query(
            """
            SELECT s FROM LivechatSeat s
            WHERE s.status = 'ONLINE'
              AND s.currentSessions < s.maxSessions
              AND s.skillGroup LIKE %:skillGroup%
            ORDER BY s.currentSessions ASC
            """)
    List<LivechatSeat> findAvailableBySkillGroup(String skillGroup);

    /** 查找所有有空闲容量的在线坐席 */
    @Query(
            """
            SELECT s FROM LivechatSeat s
            WHERE s.status = 'ONLINE'
              AND s.currentSessions < s.maxSessions
            ORDER BY s.currentSessions ASC
            """)
    List<LivechatSeat> findAllAvailable();

    List<LivechatSeat> findByStatus(SeatStatusEnum status);
}
