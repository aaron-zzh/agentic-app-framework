package com.xuejiai.aaf.module.pay.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.pay.domain.BizOrderItem;

/** 订单明细行仓储 */
public interface BizOrderItemRepository extends JpaRepository<BizOrderItem, Long> {

    List<BizOrderItem> findByOrderId(Long orderId);
}
