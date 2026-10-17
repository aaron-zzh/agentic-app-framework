package com.xuejiai.aaf.module.elder.mall;

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
import com.xuejiai.aaf.module.elder.mall.controller.ServiceOrderController;
import com.xuejiai.aaf.module.elder.mall.domain.ServiceOrder;

/** 服务预约 CRUD 资源定义。 */
public final class ServiceOrderResource {

    public static final ResourceKey KEY = ResourceKey.of("elder.mall.service-order");
    public static final String BASE_PATH = "/api/elder/mall/service-orders";
    public static final String COMMAND_CONFIRM = "SERVICE_ORDER_CONFIRM";
    public static final String COMMAND_CANCEL = "SERVICE_ORDER_CANCEL";
    public static final String COMMAND_COMPLETE = "SERVICE_ORDER_COMPLETE";

    private static final CrudResourceTypeContract<ServiceOrder> TYPES =
            CrudResourceTypeContract.fromCrudController(
                    ServiceOrderController.class, ServiceOrder.class);

    private static final CrudViewDefinition VIEW =
            CrudViewDefinition.forTypes(TYPES)
                    .withFieldSet(
                            "list",
                            Set.of(
                                    "id",
                                    "version",
                                    "elderId",
                                    "serviceItemId",
                                    "quantity",
                                    "scheduledAt",
                                    "serviceAddress",
                                    "status",
                                    "createTime",
                                    "elder",
                                    "serviceItem"))
                    .withFieldSet(
                            "detail",
                            Set.of(
                                    "id",
                                    "version",
                                    "elderId",
                                    "serviceItemId",
                                    "quantity",
                                    "scheduledAt",
                                    "serviceAddress",
                                    "notes",
                                    "status",
                                    "reminderCommandId",
                                    "confirmedAt",
                                    "completedAt",
                                    "cancelledAt",
                                    "createTime",
                                    "updateTime",
                                    "elder",
                                    "serviceItem",
                                    "createBy",
                                    "updateBy"))
                    .withFieldSet(
                            "export",
                            Set.of(
                                    "id",
                                    "elderId",
                                    "serviceItemId",
                                    "quantity",
                                    "scheduledAt",
                                    "serviceAddress",
                                    "status",
                                    "confirmedAt",
                                    "completedAt",
                                    "cancelledAt"));

    public static final CrudResourceDefinition<ServiceOrder> DEFINITION =
            CrudResourceDefinition.standard(
                    KEY,
                    TYPES,
                    new CrudResourceDescriptor("服务预约", BASE_PATH, "elder:mall-service-order"),
                    CrudCapabilityDefinition.forTypes(TYPES)
                            .without(
                                    CrudOperation.IMPORT,
                                    CrudOperation.RESTORE,
                                    CrudOperation.ARCHIVE),
                    new CrudQueryDefinition<>(
                            CrudFilterSchema.auto(),
                            Set.of(
                                    "id",
                                    "elderId",
                                    "serviceItemId",
                                    "status",
                                    "scheduledAt",
                                    "createTime"),
                            Sort.by("scheduledAt").descending().and(Sort.by("id").descending())),
                    CrudMutationDefinition.forTypes(TYPES)
                            .withCustomUpdateCommands(
                                    Map.of(
                                            COMMAND_CONFIRM,
                                            Set.of("status", "reminderCommandId", "confirmedAt"),
                                            COMMAND_CANCEL,
                                            Set.of("status", "cancelledAt"),
                                            COMMAND_COMPLETE,
                                            Set.of("status", "completedAt"))),
                    VIEW,
                    TenantScope.ORG_SHARED_WORKSPACE_OPTIONAL,
                    PersonalScope.byProperty("ownerId"));

    private ServiceOrderResource() {}
}
