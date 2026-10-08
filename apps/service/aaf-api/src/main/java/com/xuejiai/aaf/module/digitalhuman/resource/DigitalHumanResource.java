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
import com.xuejiai.aaf.module.digitalhuman.controller.DigitalHumanController;
import com.xuejiai.aaf.module.digitalhuman.domain.DigitalHuman;

/** 数字人 CRUD 资源定义：组织内共享可见，仅创建者可修改（由 Service 校验）。 */
public final class DigitalHumanResource {

    public static final ResourceKey KEY = ResourceKey.of("digital-human.digital-human");
    public static final String BASE_PATH = "/api/digital-humans";

    private static final CrudResourceTypeContract<DigitalHuman> TYPES =
            CrudResourceTypeContract.fromCrudController(
                    DigitalHumanController.class, DigitalHuman.class);

    public static final CrudResourceDefinition<DigitalHuman> DEFINITION =
            CrudResourceDefinition.standard(
                    KEY,
                    TYPES,
                    new CrudResourceDescriptor("数字人", BASE_PATH, "digital-human:digital-human"),
                    CrudCapabilityDefinition.forTypes(TYPES)
                            .without(
                                    CrudOperation.IMPORT,
                                    CrudOperation.RESTORE,
                                    CrudOperation.ARCHIVE),
                    new CrudQueryDefinition<>(
                            CrudFilterSchema.auto(),
                            Set.of(
                                    "name",
                                    "status",
                                    "assistantId",
                                    "syncModelConfig",
                                    "generationSource",
                                    "aigcProjectId",
                                    "videoCount",
                                    "totalDuration",
                                    "creditsConsumed",
                                    "lastUsedAt"),
                            Sort.by("updateTime").descending()),
                    CrudMutationDefinition.forTypes(TYPES),
                    CrudViewDefinition.forTypes(TYPES),
                    TenantScope.ORG_SHARED_WORKSPACE_OPTIONAL,
                    PersonalScope.none());

    private DigitalHumanResource() {}
}
