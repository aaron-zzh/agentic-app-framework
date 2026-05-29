package com.xuejiai.aaf.module.pay.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.pay.domain.ReconcileRecord;

/** 对账记录仓储 */
public interface ReconcileRecordRepository extends JpaRepository<ReconcileRecord, Long> {

    Optional<ReconcileRecord> findByReconcileDateAndChannelCode(LocalDate date, String channelCode);

    List<ReconcileRecord> findByReconcileDateBetween(LocalDate start, LocalDate end);
}
