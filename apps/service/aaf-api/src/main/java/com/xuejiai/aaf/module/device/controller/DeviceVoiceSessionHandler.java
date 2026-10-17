package com.xuejiai.aaf.module.device.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import com.xuejiai.aaf.module.device.security.DevicePrincipal;
import com.xuejiai.aaf.module.device.service.DeviceVoiceClient;
import com.xuejiai.aaf.module.device.service.DeviceVoiceGatewayService;
import com.xuejiai.aaf.module.voice.api.VoiceClient;
import com.xuejiai.aaf.module.voice.ws.AbstractVoiceSessionHandler;
import com.xuejiai.aaf.module.voice.ws.VoiceRuntime;

/** 设备端语音会话入口（协议 v3）：认证沿用设备凭据头，握手后协议与状态机由 {@code module.voice} 共用实现处理。 */
@Component
public class DeviceVoiceSessionHandler extends AbstractVoiceSessionHandler {

    private final DeviceVoiceGatewayService gateway;

    public DeviceVoiceSessionHandler(VoiceRuntime runtime, DeviceVoiceGatewayService gateway) {
        super(runtime);
        this.gateway = gateway;
    }

    @Override
    protected VoiceClient resolveClient(WebSocketSession session) {
        var principal = devicePrincipal(session);
        return principal == null ? null : new DeviceVoiceClient(gateway, principal);
    }

    private static DevicePrincipal devicePrincipal(WebSocketSession session) {
        var principal = session.getPrincipal();
        if (principal instanceof DevicePrincipal devicePrincipal) {
            return devicePrincipal;
        }
        if (principal instanceof Authentication authentication
                && authentication.getPrincipal() instanceof DevicePrincipal devicePrincipal) {
            return devicePrincipal;
        }
        return null;
    }
}
