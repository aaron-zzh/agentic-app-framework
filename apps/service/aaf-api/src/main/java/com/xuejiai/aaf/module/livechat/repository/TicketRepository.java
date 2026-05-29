package com.xuejiai.aaf.module.livechat.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.xuejiai.aaf.common.enums.livechat.TicketStatusEnum;
import com.xuejiai.aaf.module.livechat.domain.Ticket;

/** 工单仓储。 */
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    Optional<Ticket> findByTicketNo(String ticketNo);

    List<Ticket> findByStatusIn(List<TicketStatusEnum> statuses);

    List<Ticket> findByAssigneeIdAndStatusIn(Long assigneeId, List<TicketStatusEnum> statuses);

    List<Ticket> findByUserId(Long userId);

    /** 查询 SLA 即将超时或已超时的工单 */
    List<Ticket> findByStatusInAndSlaDueTimeBefore(List<TicketStatusEnum> statuses, LocalDateTime time);

    @Query("SELECT t.status, COUNT(t) FROM Ticket t WHERE t.deleted = false GROUP BY t.status")
    List<Object[]> countByStatus();

    @Query("SELECT t.type, COUNT(t) FROM Ticket t WHERE t.deleted = false GROUP BY t.type")
    List<Object[]> countByType();

    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.status = 'CLOSED' AND t.closedTime IS NOT NULL AND t.createTime >= :since")
    long countClosedSince(LocalDateTime since);

    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.createTime >= :since")
    long countCreatedSince(LocalDateTime since);

    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.slaDueTime < :now AND t.status IN ('PENDING','PROCESSING','CONFIRMING')")
    long countOverdue(LocalDateTime now);
}
