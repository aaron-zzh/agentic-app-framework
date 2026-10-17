package com.xuejiai.aaf.module.device;

import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Sort;

import com.xuejiai.aaf.framework.crud.definition.CrudCapabilityDefinition;
import com.xuejiai.aaf.framework.crud.definition.CrudMutationDefinition;
import com.xuejiai.aaf.framework.crud.definition.CrudOperation;
import com.xuejiai.aaf.framework.crud.definition.CrudQueryDefinition;
import com.xuejiai.aaf.framework.crud.definition.CrudResourceDefinition;
import com.xuejiai.aaf.framework.crud.definition.CrudResourceDescriptor;
import com.xuejiai.aaf.framework.crud.definition.CrudResourceTypeContract;
import com.xuejiai.aaf.framework.crud.definition.CrudViewDefinition;
import com.xuejiai.aaf.framework.crud.definition.PersonalScope;
import com.xuejiai.aaf.framework.crud.definition.ResourceKey;
import com.xuejiai.aaf.framework.crud.definition.TenantScope;
import com.xuejiai.aaf.framework.crud.filter.CrudFilterSchema;
import com.xuejiai.aaf.module.device.controller.DeviceController;
import com.xuejiai.aaf.module.device.domain.Device;

/** 设备 CRUD 资源定义。 */
public final class DeviceResource {

    public static final ResourceKey KEY = ResourceKey.of("device.device");
    public static final String BASE_PATH = "/api/device/devices";
    public static final String COMMAND_UNBIND = "DEVICE_UNBIND";

    private static final CrudResourceTypeContract<Device> TYPES =
            CrudResourceTypeContract.fromCrudController(DeviceController.class, Device.class);

    private static final CrudViewDefinition VIEW =
            CrudViewDefinition.forTypes(TYPES)
                    .withFieldSet(
                            "list",
                            Set.of(
                                    "id",
                                    "version",
                                    "name",
                                    "serialNumber",
                                    "deviceType",
                                    "zone",
                                    "status",
                                    "onlineStatus",
                                    "firmwareVersion",
                                    "lastHeartbeatAt",
                                    "scenarioKey",
                                    "subject",
                                    "ownerUser"))
                    .withFieldSet("picker", Set.of("id", "name", "serialNumber", "status"))
                    .withFieldSet(
                            "detail",
                            Set.of(
                                    "id",
                                    "version",
                                    "serialNumber",
                                    "deviceKey",
                                    "name",
                                    "model",
                                    "firmwareVersion",
                                    "deviceType",
                                    "zone",
                                    "scenarioKey",
                                    "subjectType",
                                    "subjectId",
                                    "roleKey",
                                    "ownerUserId",
                                    "credentialPrefix",
                                    "status",
                                    "onlineStatus",
                                    "bootId",
                                    "lastHeartbeatAt",
                                    "lastPersonSeenAt",
                                    "cameraStatus",
                                    "privacyMode",
                                    "capabilities",
                                    "credentialRevokedAt",
                                    "credentialLastUsedAt",
                                    "createTime",
                                    "updateTime",
                                    "subject",
                                    "ownerUser",
                                    "createBy",
                                    "updateBy"))
                    .withFieldSet(
                            "export",
                            Set.of(
                                    "id",
                                    "name",
                                    "serialNumber",
                                    "deviceType",
                                    "zone",
                                    "status",
                                    "onlineStatus",
                                    "firmwareVersion",
                                    "lastHeartbeatAt"));

    public static final CrudResourceDefinition<Device> DEFINITION =
            CrudResourceDefinition.standard(
                    KEY,
                    TYPES,
                    new CrudResourceDescriptor("设备", BASE_PATH, "device:device"),
                    CrudCapabilityDefinition.forTypes(TYPES)
                            .without(
                                    CrudOperation.IMPORT,
                                    CrudOperation.RESTORE,
                                    CrudOperation.ARCHIVE,
                                    CrudOperation.DELETE,
                                    CrudOperation.DELETE_BATCH),
                    new CrudQueryDefinition<>(
                            CrudFilterSchema.auto(),
                            Set.of(
                                    "id",
                                    "name",
                                    "serialNumber",
                                    "status",
                                    "onlineStatus",
                                    "lastHeartbeatAt"),
                            Sort.by("id").descending()),
                    CrudMutationDefinition.forTypes(TYPES)
                            .withCustomUpdateCommands(
                                    Map.of(
                                            COMMAND_UNBIND,
                                            Set.of(
                                                    "version",
                                                    "status",
                                                    "onlineStatus",
                                                    "credentialHash",
                                                    "credentialRevokedAt",
                                                    "bootId",
                                                    "bootStartedAt",
                                                    "statusSequence"))),
                    VIEW,
                    TenantScope.ORG_SHARED_WORKSPACE_OPTIONAL,
                    PersonalScope.none());

    private DeviceResource() {}
}
