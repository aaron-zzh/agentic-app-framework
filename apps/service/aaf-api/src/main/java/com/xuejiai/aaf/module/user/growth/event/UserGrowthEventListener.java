package com.xuejiai.aaf.module.user.growth.event;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.module.user.growth.service.UserGrowthService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 监听成长任务触发事件，异步推进进度（不阻塞业务主流程）。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserGrowthEventListener {

    private final UserGrowthService userGrowthService;

    @Async
    @EventListener
    public void onGrowthEvent(UserGrowthEvent event) {
        try {
            userGrowthService.incrementProgressByEvent(event.userId(), event.eventCode());
        } catch (Exception e) {
            log.warn(
                    "[UserGrowthEventListener] 推进进度失败: userId={}, event={}",
                    event.userId(),
                    event.eventCode(),
                    e);
        }
    }
}
