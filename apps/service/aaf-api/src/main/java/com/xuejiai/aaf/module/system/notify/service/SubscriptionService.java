package com.xuejiai.aaf.module.system.notify.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.module.system.notify.domain.Subscription;
import com.xuejiai.aaf.module.system.notify.repository.SubscriptionRepository;

import lombok.RequiredArgsConstructor;

/**
 * 字段变更订阅业务逻辑。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;

    /** 创建订阅 */
    @Transactional
    public Subscription create(
            Long userId, String entityType, Long entityId, String fields, String channels) {
        var sub = new Subscription();
        sub.setUserId(userId);
        sub.setEntityType(entityType);
        sub.setEntityId(entityId);
        sub.setFields(fields);
        sub.setChannels(channels);
        return subscriptionRepository.save(sub);
    }

    /** 取消订阅 */
    @Transactional
    public void cancel(Long userId, Long id) {
        var sub =
                subscriptionRepository
                        .findById(id)
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "订阅不存在"));
        if (!sub.getUserId().equals(userId)) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN, "无权取消他人订阅");
        }
        subscriptionRepository.delete(sub);
    }

    /** 查询当前用户对指定实体的订阅 */
    public List<Subscription> listByUserAndEntity(Long userId, String entityType, Long entityId) {
        return subscriptionRepository.findByUserIdAndEntityTypeAndEntityId(
                userId, entityType, entityId);
    }

    /** 查询某实体的所有订阅者 */
    public List<Subscription> findSubscribers(String entityType, Long entityId) {
        return subscriptionRepository.findByEntityTypeAndEntityId(entityType, entityId);
    }
}
