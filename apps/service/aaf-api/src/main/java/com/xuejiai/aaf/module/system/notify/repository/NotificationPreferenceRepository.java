package com.xuejiai.aaf.module.system.notify.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.system.notify.domain.NotificationPreference;

/**
 * 通知偏好数据访问层。
 *
 * @author AaronZZH & Kiro
 */
public interface NotificationPreferenceRepository
        extends JpaRepository<NotificationPreference, Long> {

    Optional<NotificationPreference> findByUserId(Long userId);
}
