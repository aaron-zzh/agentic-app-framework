package com.xuejiai.aaf.module.system.listener;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.module.system.event.EntityChangeEvent;
import com.xuejiai.aaf.module.system.service.ActivityService;

import lombok.RequiredArgsConstructor;

/** 监听实体变更事件，自动记录活动日志。 */
@Component
@RequiredArgsConstructor
public class EntityChangeListener {

    private final ActivityService activityService;

    @EventListener
    public void onEntityChange(EntityChangeEvent event) {
        activityService.record(
                event.entityType(),
                event.entityId(),
                event.action(),
                event.changes());
    }
}
