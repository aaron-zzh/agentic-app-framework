package com.xuejiai.aaf.module.device.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;
import static com.xuejiai.aaf.module.device.ErrorCodeConstants.*;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.xuejiai.aaf.common.enums.device.DeviceCameraStatusEnum;
import com.xuejiai.aaf.common.enums.device.DeviceOnlineStatusEnum;
import com.xuejiai.aaf.common.enums.device.DeviceStatusEnum;
import com.xuejiai.aaf.common.enums.device.DeviceTypeEnum;
import com.xuejiai.aaf.common.enums.device.DeviceZoneEnum;
import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.crud.definition.Patch;
import com.xuejiai.aaf.framework.crud.reference.ResourceReference;
import com.xuejiai.aaf.module.device.DeviceResource;
import com.xuejiai.aaf.module.device.api.DeviceConnectionControl;
import com.xuejiai.aaf.module.device.domain.Device;
import com.xuejiai.aaf.module.device.mapper.DeviceConvert;
import com.xuejiai.aaf.module.device.repository.DeviceRepository;
import com.xuejiai.aaf.module.device.scenario.DeviceBindingContext;
import com.xuejiai.aaf.module.device.scenario.DeviceScenario;
import com.xuejiai.aaf.module.device.scenario.DeviceScenarioAccess;
import com.xuejiai.aaf.module.device.vo.DeviceCreateDTO;
import com.xuejiai.aaf.module.device.vo.DevicePageDTO;
import com.xuejiai.aaf.module.device.vo.DeviceUpdateDTO;
import com.xuejiai.aaf.module.device.vo.DeviceVO;

/** 设备管理服务。创建和场景主体变更均经过场景绑定校验。 */
@Service
public class DeviceService
        extends BaseCrudService<Device, DeviceVO, DeviceCreateDTO, DeviceUpdateDTO, DevicePageDTO> {

    private final DeviceRepository deviceRepository;
    private final DevicePairingService pairingService;
    private final DeviceCredentialService credentialService;
    private final DeviceConnectionControl connectionControl;
    private final DeviceScenarioAccess scenarioAccess;

    public DeviceService(
            DeviceRepository deviceRepository,
            DevicePairingService pairingService,
            DeviceCredentialService credentialService,
            DeviceConnectionControl connectionControl,
            DeviceScenarioAccess scenarioAccess) {
        this.deviceRepository = deviceRepository;
        this.pairingService = pairingService;
        this.credentialService = credentialService;
        this.connectionControl = connectionControl;
        this.scenarioAccess = scenarioAccess;
    }

    @Override
    protected DeviceRepository getRepository() {
        return deviceRepository;
    }

    @Override
    protected DeviceVO toVO(Device entity) {
        return new DeviceVO(
                entity.getId(),
                entity.getVersion(),
                entity.getSerialNumber(),
                entity.getDeviceKey(),
                entity.getName(),
                entity.getModel(),
                entity.getFirmwareVersion(),
                entity.getDeviceType(),
                entity.getZone(),
                entity.getScenarioKey(),
                entity.getSubjectType(),
                entity.getSubjectId(),
                entity.getRoleKey(),
                entity.getOwnerUserId(),
                entity.getCredentialPrefix(),
                entity.getStatus(),
                entity.getOnlineStatus(),
                entity.getBootId(),
                entity.getLastHeartbeatAt(),
                entity.getLastPersonSeenAt(),
                entity.getCameraStatus(),
                entity.getPrivacyMode(),
                entity.getCapabilities(),
                entity.getCredentialRevokedAt(),
                entity.getCredentialLastUsedAt(),
                entity.getCreateTime(),
                entity.getUpdateTime(),
                null,
                null,
                null,
                null);
    }

    @Override
    protected Device toEntity(DeviceCreateDTO request) {
        var pairing = pairingService.requirePending(request.pairCode());
        if (deviceRepository.existsBySerialNumberAndStatus(
                pairing.serialNumber(), DeviceStatusEnum.BOUND.getCode())) {
            throw exception(DEVICE_ALREADY_BOUND);
        }
        var device = DeviceConvert.INSTANCE.toEntity(request);
        var scenario =
                request.scenarioKey() == null || request.scenarioKey().isBlank()
                        ? scenarioAccess.firstEnabled()
                        : scenarioAccess.requireEnabled(request.scenarioKey().trim());
        device.setScenarioKey(scenario.key());
        applySubject(device, request.subject());
        device.setRoleKey(trimToNull(request.roleKey()));
        validateBinding(device, scenario);
        device.setSerialNumber(pairing.serialNumber());
        device.setDeviceKey(UUID.randomUUID().toString().replace("-", ""));
        device.setCredentialHash(
                UUID.randomUUID().toString().replace("-", "")
                        + UUID.randomUUID().toString().replace("-", ""));
        device.setCredentialPrefix("pending");
        device.setModel(pairing.model());
        device.setFirmwareVersion(pairing.firmwareVersion());
        device.setStatus(DeviceStatusEnum.BOUND.getCode());
        device.setOnlineStatus(DeviceOnlineStatusEnum.OFFLINE.getCode());
        device.setCameraStatus(DeviceCameraStatusEnum.UNKNOWN.getCode());
        device.setPrivacyMode(false);
        device.setCapabilities("{}");
        validateMutableFields(device);
        return device;
    }

    @Override
    protected void afterCreate(Device device, DeviceCreateDTO request) {
        var claim = pairingService.claimForBinding(request.pairCode());
        if (!claim.session().serialNumber().equals(device.getSerialNumber())) {
            pairingService.restoreClaim(claim);
            throw exception(DEVICE_PAIRING_INVALID);
        }
        DeviceCredentialService.IssuedCredential credential;
        try {
            credential = credentialService.issue(device);
        } catch (RuntimeException exception) {
            pairingService.restoreClaim(claim);
            throw exception;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        pairingService.completeBinding(
                                claim, device, credential.plaintextCredential());
                    }

                    @Override
                    public void afterCompletion(int status) {
                        if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                            pairingService.restoreClaim(claim);
                        }
                    }
                });
    }

    @Override
    protected void updateEntity(Device device, DeviceUpdateDTO request) {
        requireExpectedVersion(device, request.expectedVersion());
        applyRequired(request.name(), device::setName);
        applyRequired(request.deviceType(), device::setDeviceType);
        applyRequired(request.zone(), device::setZone);
        applyRequired(request.scenarioKey(), device::setScenarioKey);
        if (!request.subject().isAbsent()) {
            applySubject(device, request.subject().valueOrNull());
        }
        applyNullableText(request.roleKey(), device::setRoleKey);
        var scenario = scenarioAccess.requireEnabled(device.getScenarioKey());
        validateBinding(device, scenario);
        validateMutableFields(device);
        device.setVersion(currentVersion(device) + 1);
    }

    @Override
    protected Specification<Device> buildSpec(DevicePageDTO request) {
        return SpecificationBuilder.<Device>builder()
                .eqIfPresent("status", request.getStatus())
                .eqIfPresent("onlineStatus", request.getOnlineStatus())
                .eqIfPresent("deviceType", request.getDeviceType())
                .eqIfPresent("scenarioKey", request.getScenarioKey())
                .eqIfPresent("ownerUserId", request.getOwnerUserId())
                .build();
    }

    @Override
    protected List<String> optionSearchFields() {
        return List.of("name", "serialNumber");
    }

    @Override
    protected String displayName(Device entity) {
        return "%s（%s）".formatted(entity.getName(), entity.getSerialNumber());
    }

    /** 解绑是具名 UPDATE 命令；凭据撤销与 Broker 踢线任一步失败都不提交状态。 */
    @Transactional
    public DeviceVO unbind(Long deviceId) {
        var plan =
                new CustomUpdatePlan<Device, UnbindCommand, Void, DeviceVO>(
                        DeviceResource.COMMAND_UNBIND,
                        Set.of(
                                "version",
                                "status",
                                "onlineStatus",
                                "credentialHash",
                                "credentialRevokedAt",
                                "bootId",
                                "bootStartedAt",
                                "statusSequence"),
                        (device, command) -> requireBound(device),
                        (device, command) -> {
                            device.setVersion(currentVersion(device) + 1);
                            device.setStatus(DeviceStatusEnum.UNBOUND.getCode());
                            device.setOnlineStatus(DeviceOnlineStatusEnum.OFFLINE.getCode());
                        },
                        (device, command) -> {
                            credentialService.revoke(device);
                            connectionControl.disconnect(device.getDeviceKey());
                            device.setBootId(null);
                            device.setBootStartedAt(null);
                            device.setStatusSequence(0L);
                            return null;
                        },
                        true,
                        (device, command, ignored) -> {},
                        (device, command, ignored) -> toVO(device));
        return executeCustomUpdateCommand(deviceId, UnbindCommand.INSTANCE, plan);
    }

    private void requireBound(Device device) {
        if (!DeviceStatusEnum.BOUND.getCode().equals(device.getStatus())) {
            throw exception(DEVICE_NOT_BOUND);
        }
    }

    private void validateMutableFields(Device device) {
        if (device.getName() == null || device.getName().isBlank()) {
            throw exception(DEVICE_REQUEST_INVALID);
        }
        requireEnum(device.getDeviceType(), DeviceTypeEnum.ARRAYS);
        requireEnum(device.getZone(), DeviceZoneEnum.ARRAYS);
    }

    private void requireEnum(String value, String[] allowedValues) {
        if (value == null || Arrays.stream(allowedValues).noneMatch(value::equals)) {
            throw exception(DEVICE_REQUEST_INVALID);
        }
    }

    private void requireExpectedVersion(Device entity, Integer expectedVersion) {
        if (expectedVersion == null || expectedVersion != currentVersion(entity)) {
            throw exception(DEVICE_VERSION_CONFLICT);
        }
    }

    private int currentVersion(Device entity) {
        return entity.getVersion() == null ? 0 : entity.getVersion();
    }

    private <T> void applyRequired(Patch<T> patch, Consumer<T> setter) {
        if (patch.isAbsent()) {
            return;
        }
        if (patch.isNullValue()) {
            throw exception(DEVICE_REQUEST_INVALID);
        }
        setter.accept(patch.valueOrNull());
    }

    private void applySubject(Device device, ResourceReference subject) {
        device.setSubjectType(subject == null ? null : subject.resource());
        device.setSubjectId(subject == null ? null : subject.id());
    }

    private void validateBinding(Device device, DeviceScenario scenario) {
        requireSubjectMatches(scenario, device.getSubjectType(), device.getSubjectId());
        scenario.validateBinding(
                new DeviceBindingContext(
                        device.getOwnerUserId(),
                        device.getOrgId(),
                        device.getWorkspaceId(),
                        device.getSubjectType(),
                        device.getSubjectId()));
    }

    /** 校验设备主体与场景声明一致；缺主体与多余主体分别给出可操作的提示。 */
    static void requireSubjectMatches(DeviceScenario scenario, String subjectType, Long subjectId) {
        var supportedSubject = scenario.supportedSubjectResourceKey();
        var hasSubject = subjectType != null || subjectId != null;
        if (supportedSubject == null) {
            if (hasSubject) {
                throw exception(DEVICE_SCENARIO_SUBJECT_UNSUPPORTED, scenario.name());
            }
            return;
        }
        if (!hasSubject) {
            var hint = scenario.subjectRequiredHint();
            throw exception(
                    DEVICE_SCENARIO_SUBJECT_REQUIRED,
                    scenario.name(),
                    hint == null || hint.isBlank() ? "请先创建对应的主体档案后再选择" : hint);
        }
        if (!supportedSubject.equals(subjectType) || subjectId == null || subjectId <= 0) {
            throw exception(DEVICE_SCENARIO_BINDING_INVALID);
        }
    }

    private void applyNullableText(Patch<String> patch, Consumer<String> setter) {
        if (!patch.isAbsent()) {
            setter.accept(trimToNull(patch.valueOrNull()));
        }
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private enum UnbindCommand {
        INSTANCE
    }
}
