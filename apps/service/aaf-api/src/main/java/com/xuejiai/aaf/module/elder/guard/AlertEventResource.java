package com.xuejiai.aaf.module.elder.guard;

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
import com.xuejiai.aaf.module.elder.guard.controller.AlertEventController;
import com.xuejiai.aaf.module.elder.guard.domain.AlertEvent;

/** 预警事件 CRUD 资源定义；预警只由系统创建，管理端负责查询与处置。 */
public final class AlertEventResource {

    public static final ResourceKey KEY = ResourceKey.of("elder.guard.alert");
    public static final String BASE_PATH = "/api/elder/guard/alerts";
    public static final String COMMAND_CLAIM = "ALERT_CLAIM";
    public static final String COMMAND_ACKNOWLEDGE = "ALERT_ACKNOWLEDGE";

    private static final CrudResourceTypeContract<AlertEvent> TYPES =
            CrudResourceTypeContract.fromCrudController(
                    AlertEventController.class, AlertEvent.class);

    private static final CrudViewDefinition VIEW =
            CrudViewDefinition.forTypes(TYPES)
                    .withFieldSet(
                            "list",
                            Set.of(
                                    "id",
                                    "version",
                                    "alertType",
                                    "level",
                                    "status",
                                    "zone",
                                    "deviceOccurredAt",
                                    "receivedAt",
                                    "late",
                                    "claimedBy",
                                    "claimedAt",
                                    "elder",
                                    "device",
                                    "claimedByUser"))
                    .withFieldSet("picker", Set.of("id", "alertType", "level", "status"))
                    .withFieldSet(
                            "detail",
                            Set.of(
                                    "id",
                                    "version",
                                    "deviceId",
                                    "elderId",
                                    "eventId",
                                    "alertType",
                                    "level",
                                    "status",
                                    "zone",
                                    "confidence",
                                    "payload",
                                    "deviceOccurredAt",
                                    "receivedAt",
                                    "late",
                                    "nextActionAt",
                                    "claimedBy",
                                    "claimedAt",
                                    "acknowledgedAt",
                                    "resolvedAt",
                                    "remark",
                                    "createTime",
                                    "updateTime",
                                    "elder",
                                    "device",
                                    "claimedByUser",
                                    "createBy",
                                    "updateBy"))
                    .withFieldSet(
                            "export",
                            Set.of(
                                    "id",
                                    "elderId",
                                    "deviceId",
                                    "alertType",
                                    "level",
                                    "status",
                                    "zone",
                                    "deviceOccurredAt",
                                    "receivedAt",
                                    "late",
                                    "claimedBy",
                                    "acknowledgedAt",
                                    "resolvedAt"));

    public static final CrudResourceDefinition<AlertEvent> DEFINITION =
            CrudResourceDefinition.standard(
                    KEY,
                    TYPES,
                    new CrudResourceDescriptor("养老预警", BASE_PATH, "elder:guard-alert"),
                    CrudCapabilityDefinition.forTypes(TYPES)
                            .without(
                                    CrudOperation.CREATE,
                                    CrudOperation.DELETE,
                                    CrudOperation.DELETE_BATCH,
                                    CrudOperation.IMPORT,
                                    CrudOperation.RESTORE,
                                    CrudOperation.ARCHIVE),
                    new CrudQueryDefinition<>(
                            CrudFilterSchema.auto(),
                            Set.of(
                                    "id",
                                    "level",
                                    "status",
                                    "deviceOccurredAt",
                                    "receivedAt",
                                    "claimedBy"),
                            Sort.by(Sort.Direction.DESC, "claimedBy")
                                    .and(Sort.by("level"))
                                    .and(Sort.by(Sort.Direction.DESC, "deviceOccurredAt"))),
                    CrudMutationDefinition.forTypes(TYPES)
                            .withCustomUpdateCommands(
                                    Map.of(
                                            COMMAND_CLAIM,
                                            Set.of("version", "claimedBy", "claimedAt"),
                                            COMMAND_ACKNOWLEDGE,
                                            Set.of("version", "status", "acknowledgedAt"))),
                    VIEW,
                    TenantScope.ORG_SHARED_WORKSPACE_OPTIONAL,
                    PersonalScope.none());

    private AlertEventResource() {}
}
