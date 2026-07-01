package com.xuejiai.aaf.framework.engine.settlement;

/** 支付结果 */
public record PayResult(
        /** 下单/发起调用本身是否成功（如 precreate 调用无异常），不代表用户已完成支付 */
        boolean success,
        /**
         * 当前支付状态。同步类渠道（余额、MOCK）下单即完成为 PAID；扫码/跳转类渠道（支付宝、微信） 下单成功仅代表二维码/跳转链接生成成功，须为
         * UNPAID，待异步回调或轮询确认后才能推进为 PAID
         */
        PayStatus status,
        String outTradeNo,
        String channelOrderNo,
        String message,
        /** 扫码支付 URL（wx_native 返回 code_url，alipay_qr 返回 qr_code），前端渲染二维码用 */
        String codeUrl) {

    /** 无 codeUrl 场景的构造（提现、失败结果等） */
    public PayResult(
            boolean success,
            PayStatus status,
            String outTradeNo,
            String channelOrderNo,
            String message) {
        this(success, status, outTradeNo, channelOrderNo, message, null);
    }
}
