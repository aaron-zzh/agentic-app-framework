package com.xuejiai.aaf.framework.sequence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.jdbc.core.JdbcTemplate;

import com.xuejiai.aaf.framework.sequence.domain.SystemSequence;
import com.xuejiai.aaf.framework.sequence.repository.SystemSequenceDateRangeRepository;
import com.xuejiai.aaf.framework.sequence.repository.SystemSequenceRepository;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class SequenceServiceTest extends BaseMockitoUnitTest {

    @Mock private SystemSequenceRepository sequenceRepo;
    @Mock private SystemSequenceDateRangeRepository dateRangeRepo;
    @Mock private SequenceTemplateParser templateParser;
    @Mock private JdbcTemplate jdbcTemplate;

    @InjectMocks private SequenceService sequenceService;

    @Nested
    @DisplayName("generate")
    class Generate {

        @Test
        @DisplayName("Given 全局模式序列 When generate Then 调用 PG SEQUENCE 并返回格式化编号")
        void should_generate_global_sequence() {
            // 准备参数
            var seq = buildSeq(1L, false);

            // mock 方法
            when(sequenceRepo.findByCodeAndActiveTrue("ORDER")).thenReturn(Optional.of(seq));
            when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), anyString()))
                    .thenReturn(1L);
            when(templateParser.build("ORD-", 1L, null, 4)).thenReturn("ORD-0001");

            // 调用
            var result = sequenceService.generate("ORDER");

            // 断言
            assertThat(result).isEqualTo("ORD-0001");
            verify(jdbcTemplate).execute(anyString()); // ensureSequence
        }

        @Test
        @DisplayName("Given 序列编码不存在 When generate Then 抛出 IllegalArgumentException")
        void should_throw_when_code_not_found() {
            // mock 方法
            when(sequenceRepo.findByCodeAndActiveTrue("UNKNOWN")).thenReturn(Optional.empty());

            // 调用 + 断言
            assertThatThrownBy(() -> sequenceService.generate("UNKNOWN"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("序列号配置不存在");
        }
    }

    @Nested
    @DisplayName("nextId")
    class NextId {

        @Test
        @DisplayName("Given 有效序列 When nextId Then 返回 Long 类型 ID")
        void should_return_long_id() {
            // 准备参数
            var seq = buildSeq(2L, false);

            // mock 方法
            when(sequenceRepo.findByCodeAndActiveTrue("GRAPH_NODE")).thenReturn(Optional.of(seq));
            when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), anyString()))
                    .thenReturn(42L);

            // 调用
            var id = sequenceService.nextId("GRAPH_NODE");

            // 断言
            assertThat(id).isEqualTo(42L);
        }
    }

    // ── 辅助方法 ──────────────────────────────────────────────

    private SystemSequence buildSeq(Long id, boolean useDateRange) {
        var seq = new SystemSequence();
        seq.setId(id);
        seq.setCode(useDateRange ? "DATE_SEQ" : "ORDER");
        seq.setPrefix("ORD-");
        seq.setNumberNext(1L);
        seq.setNumberIncrement(1);
        seq.setPadding(4);
        seq.setUseDateRange(useDateRange);
        seq.setActive(true);
        return seq;
    }
}
