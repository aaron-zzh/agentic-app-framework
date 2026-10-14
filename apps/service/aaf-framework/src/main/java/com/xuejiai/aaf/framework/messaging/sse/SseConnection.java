package com.xuejiai.aaf.framework.messaging.sse;

import java.io.IOException;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 单条 SSE 连接：携带归属用户与订阅范围，内部持有发送锁（{@link SseEmitter} 不允许并发写）与已投递游标。
 *
 * <p>游标仅由持有 {@link #lock()} 的调用方读写；{@link #send} 与 {@link #comment} 内部同样加锁（可重入）。
 */
public final class SseConnection {

    private final Long ownerId;
    private final Long orgId;
    private final Long workspaceId;
    private final SseEmitter emitter;
    private final ReentrantLock sendLock = new ReentrantLock();
    private long cursor;

    SseConnection(Long ownerId, Long orgId, Long workspaceId, SseEmitter emitter) {
        this.ownerId = ownerId;
        this.orgId = orgId;
        this.workspaceId = workspaceId;
        this.emitter = emitter;
    }

    public Long ownerId() {
        return ownerId;
    }

    public Long orgId() {
        return orgId;
    }

    public Long workspaceId() {
        return workspaceId;
    }

    public SseEmitter emitter() {
        return emitter;
    }

    public void lock() {
        sendLock.lock();
    }

    public void unlock() {
        sendLock.unlock();
    }

    /** 已投递游标，调用方须持有 {@link #lock()}。 */
    public long cursor() {
        return cursor;
    }

    public void cursor(long value) {
        this.cursor = value;
    }

    /** 发送带 id 的命名事件。 */
    public void send(long id, String name, Object data) throws IOException {
        sendLock.lock();
        try {
            emitter.send(SseEmitter.event().id(String.valueOf(id)).name(name).data(data));
        } finally {
            sendLock.unlock();
        }
    }

    /** 发送注释帧（心跳、连接确认），客户端不会当作业务事件。 */
    public void comment(String value) throws IOException {
        sendLock.lock();
        try {
            emitter.send(SseEmitter.event().comment(value));
        } finally {
            sendLock.unlock();
        }
    }

    /** 以异常结束连接；emitter 已结束时忽略。 */
    public void fail(Throwable error) {
        try {
            emitter.completeWithError(error);
        } catch (RuntimeException ignored) {
            // emitter 已结束，无需处理
        }
    }
}
