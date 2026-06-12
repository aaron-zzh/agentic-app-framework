package com.xuejiai.aaf.module.pay.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.pay.domain.PayOrder;

/** 支付订单仓储 */
public interface PayOrderRepository extends JpaRepository<PayOrder, Long> {

    Optional<PayOrder> findByMerchantOrderNo(String merchantOrderNo);

    /** 查询指定状态且在截止时间之后创建的订单（用于轮询同步） */
    List<PayOrder> findByStatusAndCreateTimeAfter(Integer status, LocalDateTime createTime);

    /** 查询已过期的待支付订单（expireTime <= now） */
    List<PayOrder> findByStatusAndExpireTimeBefore(Integer status, LocalDateTime now);
}
