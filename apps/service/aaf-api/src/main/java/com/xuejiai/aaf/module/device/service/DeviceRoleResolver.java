package com.xuejiai.aaf.module.device.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;
import static com.xuejiai.aaf.module.device.ErrorCodeConstants.DEVICE_ROLE_UNAVAILABLE;

import java.util.Set;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.module.ai.role.AssistantRoleBindingService;
import com.xuejiai.aaf.module.ai.role.RoleLibraryVO;
import com.xuejiai.aaf.module.device.scenario.DeviceScenario;
import com.xuejiai.aaf.module.device.security.DevicePrincipal;

/** 设备角色解析：设备覆盖、所有者默认角色、场景默认角色依次降级。 */
@Service
public class DeviceRoleResolver {

    private final AssistantRoleBindingService bindingService;

    public DeviceRoleResolver(AssistantRoleBindingService bindingService) {
        this.bindingService = bindingService;
    }

    public String resolve(DevicePrincipal principal, DeviceScenario scenario) {
        var library = bindingService.library(null);
        var allowed = scenario.allowedRoleKeys();
        var deviceRole = usableRole(library, principal.roleKey(), allowed);
        if (deviceRole != null) {
            return deviceRole;
        }
        var ownerDefault =
                library.items().stream()
                        .filter(RoleLibraryVO.Item::defaultRole)
                        .filter(item -> usable(item, allowed))
                        .map(item -> item.role().code())
                        .findFirst()
                        .orElse(null);
        if (ownerDefault != null) {
            return ownerDefault;
        }
        var scenarioDefault = usableRole(library, scenario.defaultRoleKey(), allowed);
        if (scenarioDefault != null) {
            return scenarioDefault;
        }
        throw exception(DEVICE_ROLE_UNAVAILABLE);
    }

    private static String usableRole(
            RoleLibraryVO library, String roleKey, Set<String> allowedRoleKeys) {
        if (roleKey == null || roleKey.isBlank()) {
            return null;
        }
        return library.items().stream()
                .filter(item -> roleKey.equals(item.role().code()))
                .filter(item -> usable(item, allowedRoleKeys))
                .map(item -> item.role().code())
                .findFirst()
                .orElse(null);
    }

    private static boolean usable(RoleLibraryVO.Item item, Set<String> allowedRoleKeys) {
        return item.bound()
                && item.enabled()
                && (allowedRoleKeys.isEmpty() || allowedRoleKeys.contains(item.role().code()));
    }
}
