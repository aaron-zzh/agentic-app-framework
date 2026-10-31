package com.xuejiai.aaf.module.device.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;
import static com.xuejiai.aaf.module.device.ErrorCodeConstants.DEVICE_PAIRING_REQUEST_INVALID;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.module.system.org.api.SystemOrgContextApi;
import com.xuejiai.aaf.module.system.user.api.UserRelationService;

/** 临时为唯一测试设备自动绑定种子管理员；联调结束后必须删除该类。 */
@Service
public class DeviceAutoBindingService {

    private static final String TEST_DEVICE_SERIAL = "modbot-d21b1f18365d578e";
    private static final String TEST_OWNER_USERNAME = "admin";

    private final UserRelationService userRelationService;
    private final SystemOrgContextApi systemOrgContextApi;
    private final DeviceService deviceService;

    public DeviceAutoBindingService(
            UserRelationService userRelationService,
            SystemOrgContextApi systemOrgContextApi,
            DeviceService deviceService) {
        this.userRelationService = userRelationService;
        this.systemOrgContextApi = systemOrgContextApi;
        this.deviceService = deviceService;
    }

    /** 仅为唯一测试设备解析固定管理员与其默认组织上下文。 */
    public Optional<AutoBindingTarget> authorize(String serialNumber) {
        if (!TEST_DEVICE_SERIAL.equals(serialNumber == null ? "" : serialNumber.trim())) {
            return Optional.empty();
        }
        var ownerUserId =
                userRelationService
                        .findActiveUserIdByUsername(TEST_OWNER_USERNAME)
                        .orElseThrow(() -> exception(DEVICE_PAIRING_REQUEST_INVALID));
        var context = systemOrgContextApi.resolvePersonalDefault(ownerUserId);
        return Optional.of(
                new AutoBindingTarget(ownerUserId, context.orgId(), context.workspaceId()));
    }

    /** 自动绑定会复用普通配对的设备创建、旧凭据撤销与新凭据签发流程。 */
    public void bind(String pairCode, AutoBindingTarget target) {
        deviceService.bindAutoProvisioned(
                pairCode, target.ownerUserId(), target.orgId(), target.workspaceId());
    }

    public record AutoBindingTarget(Long ownerUserId, Long orgId, Long workspaceId) {}
}
