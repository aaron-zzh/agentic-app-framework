package com.xuejiai.aaf.module.pay.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.pay.domain.BizOrder;

/** 业务订单仓储 */
public interface BizOrderRepository extends JpaRepository<BizOrder, Long> {

    Optional<BizOrder> findByOrderNo(String orderNo);

    Page<BizOrder> findByUserId(Long userId, Pageable pageable);

    Optional<BizOrder> findByPayOrderId(Long payOrderId);
}
