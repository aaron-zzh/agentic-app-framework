package com.xuejiai.aaf.framework.engine.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.xuejiai.aaf.test.BaseMockitoUnitTest;

/** AtomMemoryEngine 核心逻辑单元测试。 */
@MockitoSettings(strictness = Strictness.LENIENT)
class AtomMemoryEngineImplTest extends BaseMockitoUnitTest {

    @Mock private MemoryAtomRepository atomRepository;
    @Mock private MemoryRelationRepository relationRepository;
    @Mock private BundleSearchService bundleSearch;
    @Mock private TimeDecayStrategy timeDecay;

    @InjectMocks private AtomMemoryEngineImpl engine;

    @Test
    void store_应保存并返回原子() {
        var atom = createAtom("测试记忆内容");
        when(atomRepository.save(atom)).thenReturn(atom);

        var result = engine.store(atom);

        assertThat(result).isEqualTo(atom);
        verify(atomRepository).save(atom);
    }

    @Test
    void searchByVector_应调用Repository向量检索() {
        var userId = 1L;
        var vec = new float[] {0.1f, 0.2f, 0.3f};
        var expected = List.of(createAtom("结果1"));
        when(atomRepository.searchByVector(eq(userId), anyString(), eq(10))).thenReturn(expected);

        var result = engine.searchByVector(userId, vec, 10);

        assertThat(result).hasSize(1);
        verify(atomRepository).searchByVector(eq(userId), contains("[0.1,0.2,0.3]"), eq(10));
    }

    @Test
    void searchByTime_应按时间范围检索() {
        var userId = 1L;
        var start = Instant.now().minus(7, ChronoUnit.DAYS);
        var end = Instant.now();
        var expected = List.of(createAtom("时间范围内"));
        when(atomRepository.findByTimeRange(userId, start, end)).thenReturn(expected);

        var result = engine.searchByTime(userId, start, end);

        assertThat(result).hasSize(1);
    }

    @Test
    void searchHybrid_无向量时应仅按时间检索() {
        var userId = 1L;
        var start = Instant.now().minus(1, ChronoUnit.DAYS);
        var end = Instant.now();
        var atom = createAtom("时间结果");
        var query = new HybridQuery(userId, null, start, end, null, null, 5);
        var timeResults = List.of(atom);
        when(atomRepository.findByTimeRange(userId, start, end)).thenReturn(timeResults);
        when(timeDecay.score(eq(atom.getEventTime()), any(), isNull())).thenReturn(0.9);

        var result = engine.searchHybrid(query);

        assertThat(result).isNotEmpty();
    }

    @Test
    void invalidate_应标记失效() {
        var ids = List.of(UUID.randomUUID(), UUID.randomUUID());

        engine.invalidate(ids);

        verify(atomRepository).invalidate(eq(ids), any(Instant.class));
    }

    @Test
    void invalidate_空列表不应调用Repository() {
        engine.invalidate(List.of());

        verify(atomRepository, never()).invalidate(any(), any());
    }

    private MemoryAtom createAtom(String content) {
        var atom = new MemoryAtom();
        atom.setId(UUID.randomUUID());
        atom.setUserId(1L);
        atom.setScope("long_term");
        atom.setContent(content);
        atom.setEventTime(Instant.now());
        atom.setValidFrom(Instant.now());
        atom.setWeight(0.5);
        atom.setAccessCount(0);
        return atom;
    }
}
