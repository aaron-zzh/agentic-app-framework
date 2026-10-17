package com.xuejiai.aaf.module.device.service;

import com.xuejiai.aaf.module.device.security.DevicePrincipal;
import com.xuejiai.aaf.module.voice.api.VoiceClient;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * 设备端的语音会话客户端：认证主体是设备，计费主体是设备所有者，"大脑"复用 {@code POST /api/device/chat} 同一条链路（场景拦截、积分预检、助理执行）。
 *
 * <p>每条会话一个实例，不是 Spring Bean。
 */
@Slf4j
public final class DeviceVoiceClient implements VoiceClient {

    private final DeviceVoiceGatewayService gateway;
    private final DevicePrincipal principal;

    public DeviceVoiceClient(DeviceVoiceGatewayService gateway, DevicePrincipal principal) {
        this.gateway = gateway;
        this.principal = principal;
    }

    @Override
    public String clientKey() {
        return "device:" + principal.deviceId();
    }

    @Override
    public void precheck() {
        gateway.precheckAsr(principal);
    }

    @Override
    public Flux<String> reply(String text) {
        // 与 REST 对话共用默认会话线程，语音与回退路径的历史是连续的
        return gateway.chat(principal, text, null).text();
    }

    @Override
    public void settleAsr(int durationMs) {
        gateway.settleAsr(principal, durationMs);
    }

    @Override
    public void settleTts(String spokenText) {
        gateway.settleSpokenText(principal, spokenText);
    }

    @Override
    public void onReplyCancelled(String reason, String replyText, int heardSentences) {
        // 助手历史截断依赖助理执行服务的消息修改能力，目前没有对应接口，仅记录
        log.info(
                "设备语音回复被取消 deviceId={} reason={} heardSentences={} replyChars={}",
                principal.deviceId(),
                reason,
                heardSentences,
                replyText.length());
    }
}
