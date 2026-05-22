package com.xuejiai.aaf.module.system.image.job;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.module.system.image.service.AiImageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** AI 图像任务状态定时同步（Midjourney + 通义万象 wanx）。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImageSyncJob {

    private final AiImageService aiImageService;

    @Scheduled(fixedDelay = 60_000)
    public void sync() {
        try {
            aiImageService.syncMidjourneyTasks();
        } catch (Exception e) {
            log.warn("[ImageSyncJob] Midjourney 同步失败", e);
        }
        try {
            aiImageService.syncWanxTasks();
        } catch (Exception e) {
            log.warn("[ImageSyncJob] Wanx 同步失败", e);
        }
    }
}
