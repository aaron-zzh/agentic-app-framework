package com.xuejiai.aaf.module.voice.ws;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.intelligent.ai.speech.SpeechService;
import com.xuejiai.aaf.module.voice.config.VoiceSessionProperties;
import com.xuejiai.aaf.module.voice.session.SerialExecutor;
import com.xuejiai.aaf.module.voice.session.VoiceSession;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/** 语音会话的共享运行时：上游语音服务、虚拟线程执行器、定时器与空闲巡检。设备端与网页端的 WebSocket 处理器共用同一份。 */
@Slf4j
@Component
@EnableConfigurationProperties(VoiceSessionProperties.class)
public class VoiceRuntime {

    private static final long IDLE_CHECK_PERIOD_SECONDS = 15;

    private final VoiceSessionRegistry registry;
    private final VoiceSession.Deps deps;
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(
                    runnable -> {
                        var thread = new Thread(runnable, "voice-timer");
                        thread.setDaemon(true);
                        return thread;
                    });

    public VoiceRuntime(
            SpeechService speech,
            VoiceSessionProperties properties,
            VoiceSessionRegistry registry) {
        this.registry = registry;
        Executor virtual = task -> Thread.startVirtualThread(task);
        this.deps =
                new VoiceSession.Deps(
                        speech,
                        properties,
                        virtual,
                        (delayMs, task) -> {
                            var future = scheduler.schedule(task, delayMs, TimeUnit.MILLISECONDS);
                            return () -> future.cancel(false);
                        },
                        System::currentTimeMillis);
    }

    @PostConstruct
    void startIdleCheck() {
        scheduler.scheduleWithFixedDelay(
                registry::checkIdle,
                IDLE_CHECK_PERIOD_SECONDS,
                IDLE_CHECK_PERIOD_SECONDS,
                TimeUnit.SECONDS);
    }

    @PreDestroy
    void shutdown() {
        registry.closeAll("shutdown");
        scheduler.shutdownNow();
    }

    VoiceSession.Deps deps() {
        return deps;
    }

    VoiceSessionRegistry registry() {
        return registry;
    }

    /** 创建串行执行器，排空队列使用虚拟线程。 */
    SerialExecutor newActor() {
        return new SerialExecutor(task -> Thread.startVirtualThread(task));
    }
}
