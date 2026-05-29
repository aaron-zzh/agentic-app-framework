package com.xuejiai.aaf.module.billing.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.billing.domain.SubscriptionRecord;

public interface SubscriptionRecordRepository extends JpaRepository<SubscriptionRecord, Long> {

    List<SubscriptionRecord> findByPayOrderIdAndPayStatus(Long payOrderId, String payStatus);
}
