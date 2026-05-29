package com.xuejiai.aaf.module.pay.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.pay.domain.RefundOrder;

/** 退款单仓储 */
public interface RefundOrderRepository extends JpaRepository<RefundOrder, Long> {

    Optional<RefundOrder> findByRefundNo(String refundNo);

    List<RefundOrder> findByPayOrderId(Long payOrderId);

    /** 查询退款中的单据（用于重试） */
    List<RefundOrder> findByStatus(Integer status);
}
