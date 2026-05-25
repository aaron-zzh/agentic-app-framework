package com.xuejiai.aaf.framework.engine.credit;

/**
 * 积分引擎——积分的发放、消耗、查询、规则管理。
 *
 * <p>与 CreditService（智能层）的关系：CreditService 是面向智能层的简化门面，
 * CreditEngine 是底层完整实现（含规则、流水、对账）。
 * v0.2+ 实现。
 */
public interface CreditEngine {

    /** 发放积分。 */
    void grant(Long userId, long amount, String reason);

    /** 消耗积分。 */
    boolean consume(Long userId, long amount, String reason);

    /** 查询余额。 */
    long balance(Long userId);

    /** 查询流水。 */
    java.util.List<CreditRecord> history(Long userId, int limit);

    /** 积分流水记录 */
    record CreditRecord(long amount, String reason, long timestampMs) {}
}
