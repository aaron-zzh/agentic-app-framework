package com.xuejiai.aaf.module.pay.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.xuejiai.aaf.module.pay.domain.PayOrder;

/** 支付订单仓储 */
public interface PayOrderRepository extends JpaRepository<PayOrder, Long> {

    Optional<PayOrder> findByMerchantOrderNo(String merchantOrderNo);

    /** 查询指定状态且在截止时间之后创建的订单（用于轮询同步） */
    List<PayOrder> findByStatusAndCreateTimeAfter(Integer status, LocalDateTime createTime);

    /** 查询已过期的待支付订单（expireTime <= now） */
    List<PayOrder> findByStatusAndExpireTimeBefore(Integer status, LocalDateTime now);

    /**
     * M3：原子状态迁移——仅当订单仍处于 {@code fromStatus} 时才置为 {@code toStatus}。
     *
     * <p>并发回调（同一订单被渠道重推 + 定时同步任务同时命中）下，先读后判再写的写法会让两个事务
     * 同时通过状态检查、各自触发一次入账。这里把判断与更新压到一条 SQL，由数据库行锁保证只有一个 事务能拿到 {@code updated == 1}，其余得到 0 并跳过后续入账。
     *
     * @return 实际更新行数：1=本次抢到状态迁移，0=已被其他线程处理或状态不匹配
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            "UPDATE PayOrder o SET o.status = :toStatus, o.channelOrderNo = :channelOrderNo,"
                    + " o.successTime = :successTime, o.updateTime = :successTime WHERE"
                    + " o.merchantOrderNo = :merchantOrderNo AND o.status = :fromStatus AND"
                    + " o.deleted = false")
    int transitionStatus(
            @Param("merchantOrderNo") String merchantOrderNo,
            @Param("fromStatus") Integer fromStatus,
            @Param("toStatus") Integer toStatus,
            @Param("channelOrderNo") String channelOrderNo,
            @Param("successTime") LocalDateTime successTime);

    /** M3：原子关单——仅当订单仍处于 {@code fromStatus} 时才置为 {@code toStatus}（失败/关闭场景不写渠道单号与成功时间）。 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            "UPDATE PayOrder o SET o.status = :toStatus, o.updateTime = :updateTime WHERE"
                    + " o.merchantOrderNo = :merchantOrderNo AND o.status = :fromStatus AND"
                    + " o.deleted = false")
    int transitionStatusOnly(
            @Param("merchantOrderNo") String merchantOrderNo,
            @Param("fromStatus") Integer fromStatus,
            @Param("toStatus") Integer toStatus,
            @Param("updateTime") LocalDateTime updateTime);
}
