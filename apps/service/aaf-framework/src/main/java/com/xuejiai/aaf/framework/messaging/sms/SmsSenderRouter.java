package com.xuejiai.aaf.framework.messaging.sms;

import java.util.Map;

import lombok.extern.slf4j.Slf4j;

/**
 * 短信发送路由器，支持多厂商按模板动态路由。
 *
 * <p><b>路由逻辑</b>：
 *
 * <ol>
 *   <li>调用 {@link #sendWith(String, String, String, Map)} 时，按传入的 provider 选择厂商
 *   <li>调用 {@link #send(String, String, Map)} 时，使用系统默认 provider（yaml 配置）
 *   <li>模板的 {@code provider} 字段为 null 时，退回系统默认
 * </ol>
 *
 * <p><b>支持的切换场景</b>：
 *
 * <ul>
 *   <li>✅ 不同业务场景用不同厂商（如验证码用阿里云，营销短信用腾讯云）
 *   <li>✅ 修改 sys_sms_template.provider 后立即生效（无需重启）
 *   <li>❌ 不支持运行时修改厂商 AccessKey（需重启，如需支持需将厂商配置存数据库）
 * </ul>
 *
 * <p><b>初始化</b>：启动时根据 yaml 配置初始化所有已配置厂商的 client， 未配置 accessKeyId 的厂商不会注册，调用时会抛 {@link
 * IllegalArgumentException}。
 */
@Slf4j
public class SmsSenderRouter implements SmsSender {

    private final Map<String, SmsSender> senders;
    private final String defaultProvider;

    public SmsSenderRouter(Map<String, SmsSender> senders, String defaultProvider) {
        this.senders = senders;
        this.defaultProvider = defaultProvider;
    }

    @Override
    public void send(String phone, String templateCode, Map<String, String> params) {
        route(defaultProvider).send(phone, templateCode, params);
    }

    /** 按指定 provider 发送 */
    public void sendWith(
            String provider, String phone, String templateCode, Map<String, String> params) {
        route(provider).send(phone, templateCode, params);
    }

    private SmsSender route(String provider) {
        var sender = senders.get(provider != null ? provider : defaultProvider);
        if (sender == null) {
            throw new IllegalArgumentException("不支持的短信厂商: " + provider);
        }
        return sender;
    }
}
