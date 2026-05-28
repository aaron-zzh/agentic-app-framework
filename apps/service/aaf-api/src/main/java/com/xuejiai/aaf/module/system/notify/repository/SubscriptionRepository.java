package com.xuejiai.aaf.module.system.notify.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.system.notify.domain.Subscription;

/**
 * 订阅数据访问层。
 *
 * @author AaronZZH & Kiro
 */
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    /** 查询当前用户对指定实体的订阅 */
    List<Subscription> findByUserIdAndEntityTypeAndEntityId(
            Long userId, String entityType, Long entityId);

    /** 查询某实体的所有订阅者 */
    List<Subscription> findByEntityTypeAndEntityId(String entityType, Long entityId);
}
