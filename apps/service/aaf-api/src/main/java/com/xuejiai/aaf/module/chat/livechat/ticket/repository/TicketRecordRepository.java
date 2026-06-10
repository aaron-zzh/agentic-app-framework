package com.xuejiai.aaf.module.chat.livechat.ticket.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.chat.livechat.ticket.domain.TicketRecord;

/**
 * 工单操作记录数据访问层。
 *
 * @author AaronZZH & Kiro
 */
public interface TicketRecordRepository extends JpaRepository<TicketRecord, Long> {

    List<TicketRecord> findByTicketIdOrderByCreateTimeAsc(Long ticketId);
}
