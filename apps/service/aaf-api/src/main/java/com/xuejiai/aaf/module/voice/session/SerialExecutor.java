package com.xuejiai.aaf.module.voice.session;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.slf4j.Slf4j;

/**
 * 串行执行器：提交的任务按顺序逐个执行，任意时刻最多一个在运行。
 *
 * <p>会话的全部状态变更都经由它进入，因此状态机不需要锁。用 {@code spawner} 决定排空队列的线程（生产环境为虚拟线程，测试可直接在调用线程上运行）。
 */
@Slf4j
public final class SerialExecutor implements Executor {

    private final Queue<Runnable> queue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean running = new AtomicBoolean();
    private final Executor spawner;
    private volatile boolean closed;

    public SerialExecutor(Executor spawner) {
        this.spawner = spawner;
    }

    @Override
    public void execute(Runnable task) {
        if (closed) {
            return;
        }
        queue.add(task);
        schedule();
    }

    /** 关闭后丢弃尚未执行与新提交的任务。 */
    public void close() {
        closed = true;
        queue.clear();
    }

    private void schedule() {
        if (running.compareAndSet(false, true)) {
            spawner.execute(this::drain);
        }
    }

    private void drain() {
        try {
            Runnable task;
            while ((task = queue.poll()) != null) {
                try {
                    task.run();
                } catch (Throwable throwable) {
                    log.error("语音会话任务执行失败", throwable);
                }
            }
        } finally {
            running.set(false);
        }
        if (!queue.isEmpty()) {
            schedule();
        }
    }
}
