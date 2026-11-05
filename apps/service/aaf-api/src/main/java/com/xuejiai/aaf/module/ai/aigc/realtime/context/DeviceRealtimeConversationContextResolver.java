package com.xuejiai.aaf.module.ai.aigc.realtime.context;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.module.ai.aigc.realtime.RealtimeCaller;
import com.xuejiai.aaf.module.ai.aigc.realtime.RealtimeHello;
import com.xuejiai.aaf.module.ai.assistant.api.AssistantConversationContextApi;
import com.xuejiai.aaf.module.ai.assistant.api.AssistantConversationContextApi.ConversationIdentity;
import com.xuejiai.aaf.module.device.api.RealtimeDeviceContext;

/** 设备受控上下文的默认 Assistant 与角色优先级解析。 */
@Service
public class DeviceRealtimeConversationContextResolver
        implements RealtimeConversationContextResolver {

    private final AssistantConversationContextApi assistantContextApi;

    public DeviceRealtimeConversationContextResolver(
            AssistantConversationContextApi assistantContextApi) {
        this.assistantContextApi = assistantContextApi;
    }

    @Override
    public boolean supports(RealtimeCaller caller) {
        return caller instanceof RealtimeCaller.Device;
    }

    @Override
    public ResolvedRealtimeContext resolve(RealtimeCaller caller, RealtimeHello hello) {
        if (!(caller instanceof RealtimeCaller.Device deviceCaller)) {
            throw new IllegalArgumentException("设备实时调用者无效");
        }
        if (!"client".equals(hello.endpointing()) || hello.roleKey() != null) {
            throw new IllegalArgumentException("设备 hello 不允许 roleKey，endpointing 必须为 client");
        }
        var deviceContext = deviceCaller.context();
        var identity =
                new ConversationIdentity(
                        null,
                        deviceContext.ownerUserId(),
                        deviceContext.orgId(),
                        deviceContext.workspaceId());
        var assistantContext = resolveRole(identity, deviceContext);
        return new ResolvedRealtimeContext(identity, assistantContext, "client", deviceContext);
    }

    private AssistantConversationContextApi.ConversationContext resolveRole(
            ConversationIdentity identity, RealtimeDeviceContext deviceContext) {
        var deviceRole = normalizedRoleKey(deviceContext.requestedRoleKey());
        if (deviceRole != null) {
            var context = resolveAllowedRole(identity, deviceContext, deviceRole);
            if (context != null) {
                return context;
            }
        }

        // 设备链路优先跟随场景默认角色，切换场景即自动切换角色。
        var scenarioRole = normalizedRoleKey(deviceContext.scenarioDefaultRoleKey());
        if (scenarioRole != null) {
            var context = resolveAllowedRole(identity, deviceContext, scenarioRole);
            if (context != null) {
                return context;
            }
        }

        // 弃用：设备链路不再回退「所有者默认角色」，仅作兜底，后续版本移除。
        var context = resolveAllowedRole(identity, deviceContext, null);
        if (context != null) {
            return context;
        }
        throw new IllegalArgumentException("设备会话角色解析失败：设备角色与场景默认角色均不可用，请检查默认助理是否存在并绑定可用的默认角色");
    }

    private AssistantConversationContextApi.ConversationContext resolveAllowedRole(
            ConversationIdentity identity, RealtimeDeviceContext deviceContext, String roleKey) {
        try {
            var context = assistantContextApi.resolve(identity, roleKey);
            return allowed(deviceContext, context.roleKey()) ? context : null;
        } catch (IllegalArgumentException | IllegalStateException ignored) {
            return null;
        }
    }

    private static String normalizedRoleKey(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean allowed(RealtimeDeviceContext context, String roleKey) {
        return context.allowedRoleKeys().isEmpty() || context.allowedRoleKeys().contains(roleKey);
    }
}
