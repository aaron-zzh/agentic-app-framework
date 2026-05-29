package com.xuejiai.aaf.module.pay.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.pay.domain.PayOrder;

/** 支付订单仓储 */
public interface PayOrderRepository extends JpaRepository<PayOrder, Long> {

    Optional<PayOrder> findByMerchantOrderNo(String merchantOrderNo);
}
