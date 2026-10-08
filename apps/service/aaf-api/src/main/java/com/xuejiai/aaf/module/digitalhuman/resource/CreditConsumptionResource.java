package com.xuejiai.aaf.module.digitalhuman.resource;

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
import com.xuejiai.aaf.module.digitalhuman.controller.CreditConsumptionController;
import com.xuejiai.aaf.module.digitalhuman.domain.CreditConsumption;

/** 数字人积分消耗记录只读 CRUD 资源定义：仅本人可见。 */
public final class CreditConsumptionResource {

    public static final ResourceKey KEY = ResourceKey.of("digital-human.credit-consumption");
    public static final String BASE_PATH = "/api/digital-human-credit-consumptions";

    private static final CrudResourceTypeContract<CreditConsumption> TYPES =
            CrudResourceTypeContract.fromCrudController(
                    CreditConsumptionController.class, CreditConsumption.class);

    public static final CrudResourceDefinition<CreditConsumption> DEFINITION =
            CrudResourceDefinition.standard(
                    KEY,
                    TYPES,
                    new CrudResourceDescriptor(
                            "数字人积分消耗记录", BASE_PATH, "digital-human:credit-consumption"),
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
                            Set.of("digitalHumanId", "projectId", "operation", "creditsConsumed"),
                            Sort.by("id").descending()),
                    CrudMutationDefinition.forTypes(TYPES),
                    CrudViewDefinition.forTypes(TYPES),
                    TenantScope.ORG_SHARED_WORKSPACE_OPTIONAL,
                    PersonalScope.byProperty("ownerId"));

    private CreditConsumptionResource() {}
}
