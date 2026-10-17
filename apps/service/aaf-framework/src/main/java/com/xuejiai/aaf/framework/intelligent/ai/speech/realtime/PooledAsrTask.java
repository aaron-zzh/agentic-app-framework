package com.xuejiai.aaf.framework.intelligent.ai.speech.realtime;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.extern.slf4j.Slf4j;

/**
 * 池化连接上的实时识别任务。
 *
 * <p>归还规则：只有 {@link #finish}/{@link #abort} 在超时内收到 task-finished 且任务期间没有出错，连接才归还复用；其余情况关闭连接并作废。
 * 这与上游的约束一致：任务失败时服务端会关闭连接，且必须收到结束事件后才能在该连接上发起新任务。
 */
@Slf4j
final class PooledAsrTask implements AsrTask {

    private final ClientPool<AsrClient> pool;
    private final AsrClient client;
    private final AsrListener listener;
    private final long finishTimeoutMs;
    private final long cancelTimeoutMs;

    private final AtomicBoolean ended = new AtomicBoolean();
    private final AtomicInteger durationSecs = new AtomicInteger();
    private volatile boolean failed;
    private volatile boolean silent;

    private PooledAsrTask(
            ClientPool<AsrClient> pool,
            AsrClient client,
            AsrListener listener,
            long finishTimeoutMs,
            long cancelTimeoutMs) {
        this.pool = pool;
        this.client = client;
        this.listener = listener;
        this.finishTimeoutMs = finishTimeoutMs;
        this.cancelTimeoutMs = cancelTimeoutMs;
    }

    /** 借出连接并发送 run-task。池耗尽抛 {@link SpeechPoolExhaustedException}，启动失败时作废连接并原样抛出。 */
    static AsrTask open(
            ClientPool<AsrClient> pool,
            String language,
            AsrListener listener,
            long finishTimeoutMs,
            long cancelTimeoutMs) {
        var client = pool.borrow();
        var task = new PooledAsrTask(pool, client, listener, finishTimeoutMs, cancelTimeoutMs);
        try {
            client.start(language, task.new Events());
        } catch (RuntimeException exception) {
            pool.discard(client);
            throw exception;
        }
        return task;
    }

    @Override
    public void sendAudio(byte[] pcm) {
        if (ended.get() || failed) {
            return;
        }
        try {
            client.send(pcm);
        } catch (RuntimeException exception) {
            fail(exception);
        }
    }

    @Override
    public int finish() {
        return end(finishTimeoutMs, false);
    }

    @Override
    public void abort() {
        end(cancelTimeoutMs, true);
    }

    private int end(long timeoutMs, boolean quiet) {
        if (!ended.compareAndSet(false, true)) {
            return durationSecs.get();
        }
        silent = quiet;
        var clean = !failed && stopWithin(timeoutMs);
        if (clean && !failed) {
            pool.release(client);
        } else {
            pool.discard(client);
        }
        return durationSecs.get();
    }

    private boolean stopWithin(long timeoutMs) {
        var stopped = new CompletableFuture<Void>();
        Thread.startVirtualThread(
                () -> {
                    try {
                        client.stop();
                        stopped.complete(null);
                    } catch (Throwable throwable) {
                        stopped.completeExceptionally(throwable);
                    }
                });
        try {
            stopped.get(timeoutMs, TimeUnit.MILLISECONDS);
            return true;
        } catch (TimeoutException exception) {
            log.warn("ASR 等待 task-finished 超时，关闭连接: timeoutMs={}", timeoutMs);
        } catch (ExecutionException exception) {
            log.warn("ASR 结束任务失败，关闭连接", exception.getCause());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        return false;
    }

    private void fail(Throwable error) {
        if (failed) {
            return;
        }
        failed = true;
        if (!silent) {
            listener.onError(error);
        }
    }

    /** 上游事件到监听器的转发，运行在 SDK 线程。 */
    private final class Events implements AsrClient.Callback {

        @Override
        public void onPartial(String text) {
            if (!silent && !ended.get()) {
                listener.onPartial(text);
            }
        }

        @Override
        public void onSentenceEnd(String text) {
            if (!silent) {
                listener.onSentenceEnd(text);
            }
        }

        @Override
        public void onUsage(int seconds) {
            var total = durationSecs.accumulateAndGet(seconds, Math::max);
            if (!silent) {
                listener.onUsage(total);
            }
        }

        @Override
        public void onComplete() {
            // 结束由 stop() 返回表示，这里无需处理
        }

        @Override
        public void onError(Exception error) {
            fail(error);
        }
    }
}
