package com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.compiler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import io.agentscope.core.ReActAgent;
import lombok.extern.slf4j.Slf4j;

/**
 * 有界 LRU 的 {@link ReActAgent} 实例缓存，触及容量上限即淘汰最近最少使用条目并 {@code close()} 释放。
 *
 * <p>从 {@link AgentScopeSpecCompiler} 提取为独立类，供智能层其它需要"按某维度缓存 ReActAgent 实例"的场景复用（例如 L0 场景按 {@code
 * modelId} 分桶缓存零工具实例），避免各自重新实现淘汰与释放逻辑（RQ-06 的通用化）。
 */
@Slf4j
public final class BoundedAgentCache<K> {

    private final String name;
    private final int capacity;
    private final LinkedHashMap<K, ReActAgent> entries;

    public BoundedAgentCache(String name, int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("cacheCapacity 必须大于 0");
        }
        this.name = name;
        this.capacity = capacity;
        this.entries =
                new LinkedHashMap<>(16, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<K, ReActAgent> eldest) {
                        if (size() <= BoundedAgentCache.this.capacity) {
                            return false;
                        }
                        log.warn(
                                "[AgentScope编译] {} 缓存达到上限，淘汰最近最少使用实例并释放：上限={}",
                                BoundedAgentCache.this.name,
                                BoundedAgentCache.this.capacity);
                        eldest.getValue().close();
                        return true;
                    }
                };
    }

    public synchronized ReActAgent computeIfAbsent(K key, Function<K, ReActAgent> factory) {
        var existing = entries.get(key);
        if (existing != null) {
            return existing;
        }
        var created = factory.apply(key);
        entries.put(key, created);
        return created;
    }

    public synchronized void closeAll() {
        entries.values().forEach(ReActAgent::close);
        entries.clear();
    }

    public synchronized int size() {
        return entries.size();
    }
}
