package com.xuejiai.aaf.framework.intelligent.cognition.application;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.xuejiai.aaf.framework.engine.memory.AtomMemoryEngine;
import com.xuejiai.aaf.framework.engine.memory.MemoryAtom;
import com.xuejiai.aaf.framework.engine.memory.MemoryBundle;
import com.xuejiai.aaf.framework.intelligent.cognition.port.MemoryRetrievalPort;

import lombok.extern.slf4j.Slf4j;

/**
 * 记忆自有检索入口的默认实现：并行检索原子、情景 bundle、程序化三通道。
 *
 * <p>迁移自旧 {@code MemoryRetrievalService}
 * 的通道检索逻辑；意图分类与预算分配已上移至调用方（{@code UnifiedRetrievalPort}），本类只按传入配额执行检索，通道间相互隔离，单通道失败不影响其他通道。
 */
@Slf4j
public final class DefaultMemoryRetrievalPort implements MemoryRetrievalPort {

    private final AtomMemoryEngine atomMemoryEngine;

    /** 程序化记忆固定 scope 标签，对齐旧实现（`MemoryRetrievalService.java:98-102`）。 */
    private static final String PROCEDURAL_SCOPE = "procedural";

    public DefaultMemoryRetrievalPort(AtomMemoryEngine atomMemoryEngine) {
        this.atomMemoryEngine = Objects.requireNonNull(atomMemoryEngine, "atomMemoryEngine 不能为空");
    }

    @Override
    public MemoryRetrievalResult retrieve(MemoryRetrievalQuery query) {
        Objects.requireNonNull(query, "query 不能为空");
        var hasEmbedding = query.queryEmbedding() != null;
        var wantAtomic = hasEmbedding && query.atomicTopK() > 0;
        var wantEpisodic = hasEmbedding && query.episodicTopK() > 0;
        var wantProcedural = query.proceduralTopK() > 0;

        if (!wantAtomic && !wantEpisodic && !wantProcedural) {
            return MemoryRetrievalResult.empty();
        }

        List<MemoryAtom> atomicMemories = List.of();
        List<MemoryBundle> episodicBundles = List.of();
        List<MemoryAtom> proceduralMemories = List.of();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<List<MemoryAtom>> atomicFuture =
                    wantAtomic
                            ? executor.submit(
                                    () ->
                                            atomMemoryEngine.searchByVector(
                                                    query.userId(),
                                                    query.queryEmbedding(),
                                                    query.atomicTopK()))
                            : null;
            Future<List<MemoryBundle>> episodicFuture =
                    wantEpisodic
                            ? executor.submit(
                                    () ->
                                            atomMemoryEngine.searchBundles(
                                                    query.userId(),
                                                    query.queryEmbedding(),
                                                    query.episodicTopK(),
                                                    query.at()))
                            : null;
            Future<List<MemoryAtom>> proceduralFuture =
                    wantProcedural
                            ? executor.submit(
                                    () ->
                                            atomMemoryEngine.searchByScope(
                                                    query.userId(),
                                                    PROCEDURAL_SCOPE,
                                                    query.proceduralTopK()))
                            : null;

            if (atomicFuture != null) {
                atomicMemories = awaitChannel(atomicFuture, "原子记忆");
            }
            if (episodicFuture != null) {
                episodicBundles = awaitChannel(episodicFuture, "情景 bundle");
            }
            if (proceduralFuture != null) {
                proceduralMemories = awaitChannel(proceduralFuture, "程序化记忆");
            }
        }

        return new MemoryRetrievalResult(atomicMemories, episodicBundles, proceduralMemories);
    }

    /** 单通道等待结果；失败只记录并返回空，不影响其他通道，不静默扩大其他通道权限。 */
    private static <T extends List<?>> T awaitChannel(Future<T> future, String channelName) {
        try {
            return future.get();
        } catch (Exception exception) {
            log.warn("[记忆检索] {} 通道检索失败，本通道按空结果继续：{}", channelName, exception.getMessage());
            @SuppressWarnings("unchecked")
            var empty = (T) List.of();
            return empty;
        }
    }
}
