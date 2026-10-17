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
import com.xuejiai.aaf.module.elder.mall.controller.ServiceItemController;
import com.xuejiai.aaf.module.elder.mall.domain.ServiceItem;

/** 服务项目 CRUD 资源定义。 */
public final class ServiceItemResource {

    public static final ResourceKey KEY = ResourceKey.of("elder.mall.service-item");
    public static final String BASE_PATH = "/api/elder/mall/service-items";
    public static final String COMMAND_PUBLISH = "SERVICE_ITEM_PUBLISH";
    public static final String COMMAND_OFFLINE = "SERVICE_ITEM_OFFLINE";

    private static final CrudResourceTypeContract<ServiceItem> TYPES =
            CrudResourceTypeContract.fromCrudController(
                    ServiceItemController.class, ServiceItem.class);

    private static final CrudViewDefinition VIEW =
            CrudViewDefinition.forTypes(TYPES)
                    .withFieldSet(
                            "list",
                            Set.of(
                                    "id",
                                    "version",
                                    "name",
                                    "description",
                                    "category",
                                    "unit",
                                    "priceDescription",
                                    "durationMinutes",
                                    "status",
                                    "createTime"))
                    .withFieldSet("picker", Set.of("id", "name", "category", "unit", "status"))
                    .withFieldSet(
                            "catalog",
                            Set.of(
                                    "id",
                                    "name",
                                    "description",
                                    "category",
                                    "unit",
                                    "priceDescription",
                                    "durationMinutes",
                                    "status"))
                    .withFieldSet(
                            "detail",
                            Set.of(
                                    "id",
                                    "version",
                                    "name",
                                    "description",
                                    "category",
                                    "unit",
                                    "priceDescription",
                                    "durationMinutes",
                                    "status",
                                    "createTime",
                                    "updateTime",
                                    "createBy",
                                    "updateBy"))
                    .withFieldSet(
                            "export",
                            Set.of(
                                    "id",
                                    "name",
                                    "category",
                                    "unit",
                                    "priceDescription",
                                    "durationMinutes",
                                    "status"));

    public static final CrudResourceDefinition<ServiceItem> DEFINITION =
            CrudResourceDefinition.standard(
                    KEY,
                    TYPES,
                    new CrudResourceDescriptor("服务项目", BASE_PATH, "elder:mall-service-item"),
                    CrudCapabilityDefinition.forTypes(TYPES)
                            .without(
                                    CrudOperation.IMPORT,
                                    CrudOperation.RESTORE,
                                    CrudOperation.ARCHIVE),
                    new CrudQueryDefinition<>(
                            CrudFilterSchema.auto(),
                            Set.of("id", "name", "category", "status", "createTime"),
                            Sort.by("id").descending()),
                    CrudMutationDefinition.forTypes(TYPES)
                            .withCustomUpdateCommands(
                                    Map.of(
                                            COMMAND_PUBLISH, Set.of("status"),
                                            COMMAND_OFFLINE, Set.of("status"))),
                    VIEW,
                    TenantScope.ORG_SHARED_WORKSPACE_OPTIONAL,
                    PersonalScope.none());

    private ServiceItemResource() {}
}
