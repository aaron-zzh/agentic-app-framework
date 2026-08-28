package com.xuejiai.aaf.framework.intelligent.cognition.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.xuejiai.aaf.framework.engine.memory.AtomMemoryEngine;
import com.xuejiai.aaf.framework.engine.memory.MemoryAtom;
import com.xuejiai.aaf.framework.engine.memory.MemoryBundle;
import com.xuejiai.aaf.framework.intelligent.cognition.port.MemoryRetrievalPort.MemoryRetrievalQuery;

@ExtendWith(MockitoExtension.class)
class DefaultMemoryRetrievalPortTest {

    @Mock private AtomMemoryEngine atomMemoryEngine;

    @Test
    void parallelRetrievesAllThreeChannelsWhenQuotaPositive() {
        var atom = atom("原子记忆内容");
        var bundle = new MemoryBundle(List.of(atom), List.of(), 0.9);
        var procedural = atom("程序化经验内容");
        when(atomMemoryEngine.searchByVector(any(), any(), anyInt())).thenReturn(List.of(atom));
        when(atomMemoryEngine.searchBundles(any(), any(), anyInt(), any()))
                .thenReturn(List.of(bundle));
        when(atomMemoryEngine.searchByScope(any(), eq("procedural"), anyInt()))
                .thenReturn(List.of(procedural));

        var port = new DefaultMemoryRetrievalPort(atomMemoryEngine);
        var result =
                port.retrieve(
                        new MemoryRetrievalQuery(
                                1L, "怎么办", new float[] {0.1f}, 6, 4, 2, Instant.now()));

        assertThat(result.atomicMemories()).containsExactly(atom);
        assertThat(result.episodicBundles()).containsExactly(bundle);
        assertThat(result.proceduralMemories()).containsExactly(procedural);
    }

    @Test
    void skipsAtomicAndEpisodicWhenEmbeddingMissing() {
        var procedural = atom("程序化经验内容");
        when(atomMemoryEngine.searchByScope(any(), eq("procedural"), anyInt()))
                .thenReturn(List.of(procedural));

        var port = new DefaultMemoryRetrievalPort(atomMemoryEngine);
        var result =
                port.retrieve(new MemoryRetrievalQuery(1L, "怎么办", null, 6, 4, 2, Instant.now()));

        assertThat(result.atomicMemories()).isEmpty();
        assertThat(result.episodicBundles()).isEmpty();
        assertThat(result.proceduralMemories()).containsExactly(procedural);
    }

    @Test
    void singleChannelFailureDoesNotAffectOtherChannels() {
        var atom = atom("原子记忆内容");
        when(atomMemoryEngine.searchByVector(any(), any(), anyInt())).thenReturn(List.of(atom));
        when(atomMemoryEngine.searchBundles(any(), any(), anyInt(), any()))
                .thenThrow(new IllegalStateException("bundle 检索失败"));

        var port = new DefaultMemoryRetrievalPort(atomMemoryEngine);
        var result =
                port.retrieve(
                        new MemoryRetrievalQuery(
                                1L, "怎么办", new float[] {0.1f}, 6, 4, 0, Instant.now()));

        assertThat(result.atomicMemories()).containsExactly(atom);
        assertThat(result.episodicBundles()).isEmpty();
    }

    @Test
    void returnsEmptyResultWhenAllQuotasZero() {
        var port = new DefaultMemoryRetrievalPort(atomMemoryEngine);
        var result =
                port.retrieve(
                        new MemoryRetrievalQuery(
                                1L, "怎么办", new float[] {0.1f}, 0, 0, 0, Instant.now()));

        assertThat(result.atomicMemories()).isEmpty();
        assertThat(result.episodicBundles()).isEmpty();
        assertThat(result.proceduralMemories()).isEmpty();
    }

    private static MemoryAtom atom(String content) {
        var atom = new MemoryAtom();
        atom.setId(UUID.randomUUID());
        atom.setUserId(1L);
        atom.setScope("long_term");
        atom.setContent(content);
        atom.setEventTime(Instant.now());
        atom.setValidFrom(Instant.now());
        atom.setWeight(0.5);
        atom.setAccessCount(0);
        atom.setCreatedAt(Instant.now());
        return atom;
    }
}
