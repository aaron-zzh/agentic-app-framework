package com.xuejiai.aaf.module.system.notify.service;

import java.time.LocalTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.module.system.log.domain.domain.NotificationPreference;
import com.xuejiai.aaf.module.system.notify.repository.NotificationPreferenceRepository;

import lombok.RequiredArgsConstructor;

/** 通知偏好业务逻辑。 */
@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository repository;

    /** 获取用户偏好，不存在则返回默认值 */
    public NotificationPreference getByUserId(Long userId) {
        return repository
                .findByUserId(userId)
                .orElseGet(
                        () -> {
                            var pref = new NotificationPreference();
                            pref.setUserId(userId);
                            return pref;
                        });
    }

    /** 更新偏好（upsert） */
    @Transactional
    public NotificationPreference upsert(
            Long userId, String preferences, LocalTime quietStart, LocalTime quietEnd) {
        var pref =
                repository
                        .findByUserId(userId)
                        .orElseGet(
                                () -> {
                                    var p = new NotificationPreference();
                                    p.setUserId(userId);
                                    return p;
                                });
        pref.setPreferences(preferences);
        pref.setQuietStart(quietStart);
        pref.setQuietEnd(quietEnd);
        return repository.save(pref);
    }
}
