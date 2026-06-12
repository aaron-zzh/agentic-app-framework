package com.xuejiai.aaf.module.pay.handler;

/**
 * 支付成功回调处理器——按业务订单类型分发。
 *
 * <p>实现类通过 Spring Bean 自动注册，Controller 按 {@link #bizOrderType()} 路由。
 */
public interface PaySuccessHandler {

    /** 对应的业务订单类型（BizOrderTypeEnum.code） */
    String bizOrderType();

    /** 支付成功后执行业务逻辑 */
    void onPaySuccess(Long payOrderId);
}
