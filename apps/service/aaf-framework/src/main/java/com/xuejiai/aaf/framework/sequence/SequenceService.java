package com.xuejiai.aaf.framework.sequence;

import java.time.LocalDate;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.sequence.domain.SystemSequenceDateRange;
import com.xuejiai.aaf.framework.sequence.repository.SystemSequenceDateRangeRepository;
import com.xuejiai.aaf.framework.sequence.repository.SystemSequenceRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 序列号生成服务。
 *
 * <p>基于 PostgreSQL SEQUENCE 保证并发安全，支持全局模式和按月分段模式。
 *
 * <p>使用示例：
 *
 * <pre>{@code
 * String no = sequenceService.generate("ORDER");   // → "ORD-20260521-0001"
 * Long id   = sequenceService.nextId("GRAPH_NODE"); // → 10001L
 * }</pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SequenceService {

    private final SystemSequenceRepository sequenceRepo;
    private final SystemSequenceDateRangeRepository dateRangeRepo;
    private final SequenceTemplateParser templateParser;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 生成格式化序列号（含前缀/后缀模板）。
     *
     * @param code 序列编码
     * @return 格式化序列号，如 "ORD-20260521-0001"
     */
    @Transactional
    public String generate(String code) {
        var seq =
                sequenceRepo
                        .findByCodeAndActiveTrue(code)
                        .orElseThrow(() -> new IllegalArgumentException("序列号配置不存在: " + code));
        long number =
                Boolean.TRUE.equals(seq.getUseDateRange())
                        ? nextDateRange(seq.getId())
                        : nextGlobal(seq.getId(), seq.getNumberNext(), seq.getNumberIncrement());
        return templateParser.build(seq.getPrefix(), number, seq.getSuffix(), seq.getPadding());
    }

    /**
     * 生成纯数字 ID，适用于 Neo4j 节点等需要 Long 类型 ID 的场景。
     *
     * @param code 序列编码
     * @return 下一个 Long ID
     */
    @Transactional
    public Long nextId(String code) {
        var seq =
                sequenceRepo
                        .findByCodeAndActiveTrue(code)
                        .orElseThrow(() -> new IllegalArgumentException("序列号配置不存在: " + code));
        return nextGlobal(seq.getId(), seq.getNumberNext(), seq.getNumberIncrement());
    }

    /**
     * 批量预分配序列号，返回起始值，调用方自行累加步长。
     *
     * @param code 序列编码
     * @param count 预分配数量
     * @return 起始序列号
     */
    @Transactional
    public Long generateBatch(String code, int count) {
        var seq =
                sequenceRepo
                        .findByCodeAndActiveTrue(code)
                        .orElseThrow(() -> new IllegalArgumentException("序列号配置不存在: " + code));
        var pgSeq = "seq_sys_sequence_" + seq.getId();
        ensureSequence(pgSeq, seq.getNumberNext(), seq.getNumberIncrement());
        // 通过 generate_series 一次性消耗 count 个值，返回第一个
        var results =
                jdbcTemplate.queryForList(
                        "SELECT nextval(?) FROM generate_series(1, ?)", Long.class, pgSeq, count);
        return results.get(0);
    }

    // ── 内部实现 ──────────────────────────────────────────────

    private long nextGlobal(Long seqId, long startValue, int increment) {
        var pgSeq = "seq_sys_sequence_" + seqId;
        ensureSequence(pgSeq, startValue, increment);
        return jdbcTemplate.queryForObject("SELECT nextval(?)", Long.class, pgSeq);
    }

    private long nextDateRange(Long seqId) {
        var today = LocalDate.now();
        var dateRange =
                dateRangeRepo
                        .findBySequenceIdAndDateFromLessThanEqualAndDateToGreaterThanEqual(
                                seqId, today, today)
                        .orElseGet(() -> createDateRange(seqId, today));
        var pgSeq = "seq_sys_date_range_" + dateRange.getId();
        ensureSequence(pgSeq, dateRange.getNumberNext(), 1);
        return jdbcTemplate.queryForObject("SELECT nextval(?)", Long.class, pgSeq);
    }

    private void ensureSequence(String pgSeq, long startValue, int increment) {
        jdbcTemplate.execute(
                "CREATE SEQUENCE IF NOT EXISTS "
                        + pgSeq
                        + " START "
                        + startValue
                        + " INCREMENT "
                        + increment);
    }

    private SystemSequenceDateRange createDateRange(Long seqId, LocalDate date) {
        var range = new SystemSequenceDateRange();
        range.setSequenceId(seqId);
        range.setDateFrom(date.withDayOfMonth(1));
        range.setDateTo(date.withDayOfMonth(date.lengthOfMonth()));
        range.setNumberNext(1L);
        return dateRangeRepo.save(range);
    }
}
