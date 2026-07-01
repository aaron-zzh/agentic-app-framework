package com.xuejiai.aaf.framework.engine.settlement;

/** 支付状态 */
public enum PayStatus {
    PAID,
    UNPAID,
    CLOSED,
    REFUNDED,
    /** 渠道侧查无此交易（如支付宝 ACQ.TRADE_NOT_EXIST）。区别于查询异常（null）：明确的业务判定，可用于提前关闭死单 */
    NOT_FOUND
}
