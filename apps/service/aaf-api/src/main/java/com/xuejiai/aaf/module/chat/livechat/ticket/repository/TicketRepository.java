package com.xuejiai.aaf.module.chat.livechat.ticket.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import com.xuejiai.aaf.module.chat.livechat.ticket.domain.Ticket;

/**
 * 客服工单数据访问层。
 *
 * @author AaronZZH & Kiro
 */
public interface TicketRepository
        extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {

    List<Ticket> findByUserId(Long userId);

    List<Ticket> findByAssigneeId(Long assigneeId);

    List<Ticket> findByConversationId(Long conversationId);

    Optional<Ticket> findByTicketNo(String ticketNo);

    // ========== TicketService 需要的查询 ==========

    /** 按受理人和状态集合查询 */
    List<Ticket> findByAssigneeIdAndStatusIn(Long assigneeId, List<String> statuses);

    /** 按状态集合和 SLA 截止时间查询（超时工单） */
    List<Ticket> findByStatusInAndSlaDueTimeBefore(List<String> statuses, LocalDateTime slaDueTime);

    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.createTime >= :since AND t.deleted = false")
    long countCreatedSince(LocalDateTime since);

    @Query(
            "SELECT COUNT(t) FROM Ticket t WHERE t.status = 'CLOSED' AND t.closedTime >= :since AND t.deleted = false")
    long countClosedSince(LocalDateTime since);

    @Query(
            "SELECT COUNT(t) FROM Ticket t WHERE t.status IN ('PENDING','PROCESSING','CONFIRMING') AND t.slaDueTime < :now AND t.deleted = false")
    long countOverdue(LocalDateTime now);

    @Query("SELECT t.status, COUNT(t) FROM Ticket t WHERE t.deleted = false GROUP BY t.status")
    List<Object[]> countByStatus();

    @Query("SELECT t.type, COUNT(t) FROM Ticket t WHERE t.deleted = false GROUP BY t.type")
    List<Object[]> countByType();
}
