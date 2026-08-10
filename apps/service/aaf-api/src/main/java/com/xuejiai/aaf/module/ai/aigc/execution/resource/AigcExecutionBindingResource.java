package com.xuejiai.aaf.module.ai.aigc.execution.resource;

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
import com.xuejiai.aaf.module.ai.aigc.execution.controller.AigcExecutionBindingController;
import com.xuejiai.aaf.module.ai.aigc.execution.domain.AigcExecutionBinding;

public final class AigcExecutionBindingResource {

    public static final ResourceKey KEY = ResourceKey.of("aigc.execution-binding");
    public static final String BASE_PATH = "/api/aigc/execution-bindings";

    private static final CrudResourceTypeContract<AigcExecutionBinding> TYPES =
            CrudResourceTypeContract.fromCrudController(
                    AigcExecutionBindingController.class, AigcExecutionBinding.class);

    public static final CrudResourceDefinition<AigcExecutionBinding> DEFINITION =
            CrudResourceDefinition.standard(
                    KEY,
                    TYPES,
                    new CrudResourceDescriptor("AIGC 执行绑定", BASE_PATH, "aigc:execution-binding"),
                    CrudCapabilityDefinition.forTypes(TYPES)
                            .without(
                                    CrudOperation.DELETE_BATCH,
                                    CrudOperation.IMPORT,
                                    CrudOperation.RESTORE,
                                    CrudOperation.ARCHIVE),
                    new CrudQueryDefinition<>(
                            CrudFilterSchema.auto(),
                            Set.of(
                                    "actionKey",
                                    "projectTypeCode",
                                    "domainExtensionCode",
                                    "productionMode",
                                    "channelCode",
                                    "targetType",
                                    "targetRef",
                                    "bindingVersion",
                                    "priority",
                                    "confirmationRequired",
                                    "estimatedCredits",
                                    "status"),
                            Sort.by(Sort.Order.asc("actionKey"), Sort.Order.desc("priority"))),
                    CrudMutationDefinition.forTypes(TYPES),
                    CrudViewDefinition.forTypes(TYPES),
                    TenantScope.GLOBAL,
                    PersonalScope.none());

    private AigcExecutionBindingResource() {}
}
