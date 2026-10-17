package com.xuejiai.aaf.framework.intelligent.ai.speech.realtime;

import java.time.Duration;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

import lombok.extern.slf4j.Slf4j;

/**
 * 上游语音客户端对象池（基于 commons-pool2）。
 *
 * <ul>
 *   <li>硬上限 {@code size}：池满时最多等待 {@code borrowTimeoutMs}，仍无空闲则抛 {@link
 *       SpeechPoolExhaustedException}， 不会像 SDK 默认那样额外创建无法复用的新对象
 *   <li>空闲对象存活不超过 {@code idleEvictSeconds}：上游在任务结束 60 秒后断开连接，及时销毁可避免复用已被服务端断开的连接
 *   <li>只有正常结束的对象才能 {@link #release}；失败、超时、未收到结束事件的对象必须 {@link #discard}
 * </ul>
 */
@Slf4j
public final class ClientPool<C> implements AutoCloseable {

    private final String name;
    private final GenericObjectPool<C> pool;
    private final Consumer<C> closer;

    public ClientPool(
            String name,
            int size,
            long borrowTimeoutMs,
            long idleEvictSeconds,
            Supplier<C> factory,
            Consumer<C> closer) {
        this.name = name;
        this.closer = closer;
        var config = new GenericObjectPoolConfig<C>();
        config.setMaxTotal(size);
        config.setMaxIdle(size);
        config.setMinIdle(0);
        config.setBlockWhenExhausted(true);
        config.setMaxWait(Duration.ofMillis(borrowTimeoutMs));
        config.setMinEvictableIdleDuration(Duration.ofSeconds(idleEvictSeconds));
        config.setTimeBetweenEvictionRuns(Duration.ofSeconds(Math.max(1, idleEvictSeconds / 5)));
        config.setJmxEnabled(false);
        this.pool =
                new GenericObjectPool<>(
                        new BasePooledObjectFactory<C>() {
                            @Override
                            public C create() {
                                return factory.get();
                            }

                            @Override
                            public PooledObject<C> wrap(C object) {
                                return new DefaultPooledObject<>(object);
                            }

                            @Override
                            public void destroyObject(PooledObject<C> pooled) {
                                closeQuietly(pooled.getObject());
                            }
                        },
                        config);
    }

    /** 借出一个对象。 */
    public C borrow() {
        try {
            return pool.borrowObject();
        } catch (NoSuchElementException exception) {
            log.warn("语音对象池耗尽: pool={}, {}", name, snapshot());
            throw new SpeechPoolExhaustedException("语音对象池耗尽: " + name, exception);
        } catch (Exception exception) {
            throw new IllegalStateException("语音对象池借出失败: " + name, exception);
        }
    }

    /** 归还一个已正常结束任务的对象。 */
    public void release(C client) {
        try {
            pool.returnObject(client);
        } catch (RuntimeException exception) {
            log.warn("语音对象归还失败，改为作废: pool={}", name, exception);
            closeQuietly(client);
        }
    }

    /** 作废对象：关闭连接并从池中移除，不再复用。 */
    public void discard(C client) {
        try {
            pool.invalidateObject(client);
        } catch (Exception exception) {
            log.debug("语音对象作废时池报错，直接关闭: pool={}", name, exception);
            closeQuietly(client);
        }
    }

    /** 当前池状态，用于日志与指标。 */
    public Snapshot snapshot() {
        return new Snapshot(
                pool.getNumActive(), pool.getNumIdle(), pool.getNumWaiters(), pool.getMaxTotal());
    }

    @Override
    public void close() {
        pool.close();
    }

    private void closeQuietly(C client) {
        try {
            closer.accept(client);
        } catch (RuntimeException exception) {
            log.debug("语音连接关闭失败: pool={}", name, exception);
        }
    }

    /** 池状态快照。 */
    public record Snapshot(int active, int idle, int waiters, int max) {}
}
