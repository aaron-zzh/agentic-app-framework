package com.xuejiai.aaf.common.enums.pay;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 支付渠道枚举，对应字典 pay_channel_code。 */
@Getter
@AllArgsConstructor
public enum PayChannelEnum {
    WX_PUB("wx_pub", "微信公众号支付"),
    WX_LITE("wx_lite", "微信小程序支付"),
    WX_APP("wx_app", "微信 App 支付"),
    WX_NATIVE("wx_native", "微信扫码支付"),
    ALIPAY_PC("alipay_pc", "支付宝 PC 网站"),
    ALIPAY_WAP("alipay_wap", "支付宝 Wap 网站"),
    ALIPAY_APP("alipay_app", "支付宝 App 支付"),
    ALIPAY_QR("alipay_qr", "支付宝扫码支付");

    private final String code;
    private final String label;
}
