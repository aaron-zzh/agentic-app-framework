package com.xuejiai.aaf.framework.intelligent.ai.speech.realtime;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.slf4j.Slf4j;

/**
 * 池化连接上的实时合成任务（一段文本）。
 *
 * <p>归还规则：收到上游结束事件且任务没有出错才归还复用；出错、取消后超时未收到结束事件，都关闭连接并作废。取消后监听器不再收到任何回调。
 */
@Slf4j
final class PooledTtsTask implements TtsTask {

    private final ClientPool<TtsClient> pool;
    private final TtsClient client;
    private final TtsListener listener;
    private final long cancelTimeoutMs;

    private final AtomicBoolean released = new AtomicBoolean();
    private final CountDownLatch finished = new CountDownLatch(1);
    private final AtomicBoolean cancelRequested = new AtomicBoolean();
    private volatile boolean cancelled;
    private volatile boolean failed;
    private volatile boolean started;

    private PooledTtsTask(
            ClientPool<TtsClient> pool,
            TtsClient client,
            TtsListener listener,
            long cancelTimeoutMs) {
        this.pool = pool;
        this.client = client;
        this.listener = listener;
        this.cancelTimeoutMs = cancelTimeoutMs;
    }

    /**
     * 借出连接并在虚拟线程上启动合成（SDK 的 {@code call} 会阻塞到任务 started）。池耗尽同步抛 {@link
     * SpeechPoolExhaustedException}；启动失败经监听器 {@link TtsListener#onError} 通知。
     */
    static TtsTask start(
            ClientPool<TtsClient> pool,
            String text,
            String voice,
            TtsListener listener,
            long cancelTimeoutMs) {
        var client = pool.borrow();
        var task = new PooledTtsTask(pool, client, listener, cancelTimeoutMs);
        Thread.startVirtualThread(() -> task.run(text, voice));
        return task;
    }

    private void run(String text, String voice) {
        try {
            client.start(text, voice, new Events());
            started = true;
        } catch (RuntimeException exception) {
            onFailure(exception);
            return;
        }
        if (cancelled) {
            sendCancel();
        }
    }

    @Override
    public void cancel() {
        if (cancelled) {
            return;
        }
        cancelled = true;
        if (started) {
            sendCancel();
        }
        // 取消后等上游结束事件决定归还还是作废，不阻塞调用线程
        Thread.startVirtualThread(this::settleAfterCancel);
    }

    private void sendCancel() {
        if (!cancelRequested.compareAndSet(false, true)) {
            return;
        }
        try {
            client.cancel();
        } catch (RuntimeException exception) {
            log.debug("TTS 取消指令发送失败", exception);
        }
    }

    private void settleAfterCancel() {
        var finishedInTime = false;
        try {
            finishedInTime = finished.await(cancelTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        if (finishedInTime && !failed) {
            releaseOnce(true);
        } else {
            releaseOnce(false);
        }
    }

    private void onFailure(Throwable error) {
        failed = true;
        finished.countDown();
        releaseOnce(false);
        if (!cancelled) {
            listener.onError(error);
        }
    }

    private void releaseOnce(boolean reusable) {
        if (!released.compareAndSet(false, true)) {
            return;
        }
        if (reusable) {
            pool.release(client);
        } else {
            pool.discard(client);
        }
    }

    /** 上游事件到监听器的转发，运行在 SDK 线程。 */
    private final class Events implements TtsClient.Callback {

        @Override
        public void onAudio(byte[] pcm) {
            if (!cancelled && !failed) {
                listener.onAudio(pcm);
            }
        }

        @Override
        public void onComplete() {
            finished.countDown();
            if (cancelled) {
                return;
            }
            releaseOnce(!failed);
            listener.onComplete();
        }

        @Override
        public void onError(Exception error) {
            onFailure(error);
        }
    }
}
