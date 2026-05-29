package com.xuejiai.aaf.module.livechat.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.livechat.domain.TicketRecord;

/** 工单流转记录仓储。 */
public interface TicketRecordRepository extends JpaRepository<TicketRecord, Long> {

    List<TicketRecord> findByTicketIdOrderByCreateTimeAsc(Long ticketId);
}
