package com.xuejiai.aaf.framework.engine.settlement;

/** 支付状态查询结果 */
public record QueryResult(PayStatus status, String channelOrderNo) {

    /** 无渠道交易号场景的快捷构造（如 UNPAID/NOT_FOUND 场景，或查询异常） */
    public QueryResult(PayStatus status) {
        this(status, null);
    }
}
