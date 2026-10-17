package com.xuejiai.aaf.module.device.scenario;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.xuejiai.aaf.module.device.security.DevicePrincipal;

import reactor.core.publisher.Flux;

/** 设备业务场景扩展点。通用设备层只依赖本契约，不感知具体场景。 */
public interface DeviceScenario {

    String key();

    String name();

    String defaultRoleKey();

    default Set<String> allowedRoleKeys() {
        return Set.of();
    }

    default String supportedSubjectResourceKey() {
        return null;
    }

    /** 场景需要主体、但设备未选择主体时给运营的下一步提示（例如先到哪里新建档案）；返回 null 使用通用提示。 */
    default String subjectRequiredHint() {
        return null;
    }

    /**
     * 本场景可下发的业务能力名（COMMAND 与 CANCEL 方向）。配置下发使用的 {@code config.update} 是通用能力， 无需声明；未声明的能力会被设备消息入口拒绝。
     */
    default Set<String> commandCapabilities() {
        return Set.of();
    }

    /** 按场景主体下发指令时选用的设备类型；返回 null 表示不限类型，取主体名下最早绑定的设备。 */
    default String commandDeviceType() {
        return null;
    }

    default void validateBinding(DeviceBindingContext context) {}

    default Map<String, Object> briefExtras(DevicePrincipal principal) {
        return Map.of();
    }

    default Optional<ChatIntercept> preChat(DevicePrincipal principal, String text) {
        return Optional.empty();
    }

    default Flux<String> postChat(DevicePrincipal principal, String text, Flux<String> stream) {
        return stream;
    }

    default AutoCloseable openToolScope(DevicePrincipal principal) {
        return () -> {};
    }

    default Map<String, Object> chatVariables(DevicePrincipal principal) {
        return Map.of();
    }
}
