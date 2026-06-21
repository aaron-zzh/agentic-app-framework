package com.xuejiai.aaf.framework.engine.settlement;

/** 支付结果 */
public record PayResult(
        boolean success,
        String outTradeNo,
        String channelOrderNo,
        String message,
        /** 扫码支付 URL（wx_native 返回 code_url，alipay_qr 返回 qr_code），前端渲染二维码用 */
        String codeUrl) {

    /** 兼容旧构造（无 codeUrl） */
    public PayResult(boolean success, String outTradeNo, String channelOrderNo, String message) {
        this(success, outTradeNo, channelOrderNo, message, null);
    }
}
