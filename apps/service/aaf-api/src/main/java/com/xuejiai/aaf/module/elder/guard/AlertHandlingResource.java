package com.xuejiai.aaf.module.elder.guard;

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
import com.xuejiai.aaf.module.elder.guard.controller.AlertHandlingController;
import com.xuejiai.aaf.module.elder.guard.domain.AlertHandling;

/** 预警处理记录资源定义；管理端只读，记录由预警流程追加。 */
public final class AlertHandlingResource {

    public static final ResourceKey KEY = ResourceKey.of("elder.guard.alert-handling");
    public static final String BASE_PATH = "/api/elder/guard/alert-handlings";

    private static final CrudResourceTypeContract<AlertHandling> TYPES =
            CrudResourceTypeContract.fromCrudController(
                    AlertHandlingController.class, AlertHandling.class);

    private static final CrudViewDefinition VIEW =
            CrudViewDefinition.forTypes(TYPES)
                    .withFieldSet(
                            "list",
                            Set.of(
                                    "id",
                                    "alertId",
                                    "handlingType",
                                    "channel",
                                    "status",
                                    "recipientType",
                                    "operatorUserId",
                                    "acknowledgedContactId",
                                    "handledAt",
                                    "alert",
                                    "operatorUser",
                                    "acknowledgedContact"))
                    .withFieldSet("picker", Set.of("id", "handlingType", "handledAt"))
                    .withFieldSet(
                            "detail",
                            Set.of(
                                    "id",
                                    "alertId",
                                    "handlingType",
                                    "channel",
                                    "status",
                                    "recipientType",
                                    "recipientId",
                                    "operatorUserId",
                                    "acknowledgedContactId",
                                    "detail",
                                    "errorMessage",
                                    "handledAt",
                                    "createTime",
                                    "alert",
                                    "operatorUser",
                                    "acknowledgedContact",
                                    "createBy"))
                    .withFieldSet(
                            "export",
                            Set.of(
                                    "id",
                                    "alertId",
                                    "handlingType",
                                    "channel",
                                    "status",
                                    "recipientType",
                                    "recipientId",
                                    "operatorUserId",
                                    "acknowledgedContactId",
                                    "detail",
                                    "errorMessage",
                                    "handledAt"));

    public static final CrudResourceDefinition<AlertHandling> DEFINITION =
            CrudResourceDefinition.standard(
                    KEY,
                    TYPES,
                    new CrudResourceDescriptor("预警处理记录", BASE_PATH, "elder:guard-alert-handling"),
                    CrudCapabilityDefinition.forTypes(TYPES)
                            .without(
                                    CrudOperation.CREATE,
                                    CrudOperation.UPDATE,
                                    CrudOperation.DELETE,
                                    CrudOperation.DELETE_BATCH,
                                    CrudOperation.IMPORT,
                                    CrudOperation.RESTORE,
                                    CrudOperation.ARCHIVE),
                    new CrudQueryDefinition<>(
                            CrudFilterSchema.auto(),
                            Set.of("id", "handledAt"),
                            Sort.by(Sort.Direction.DESC, "handledAt")
                                    .and(Sort.by(Sort.Direction.DESC, "id"))),
                    CrudMutationDefinition.forTypes(TYPES),
                    VIEW,
                    TenantScope.ORG_SHARED_WORKSPACE_OPTIONAL,
                    PersonalScope.none());

    private AlertHandlingResource() {}
}
