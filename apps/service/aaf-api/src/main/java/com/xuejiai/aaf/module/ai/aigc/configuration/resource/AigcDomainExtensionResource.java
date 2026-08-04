package com.xuejiai.aaf.module.ai.aigc.configuration.resource;

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
import com.xuejiai.aaf.module.ai.aigc.configuration.controller.AigcDomainExtensionController;
import com.xuejiai.aaf.module.ai.aigc.configuration.domain.AigcDomainExtension;

/** AIGC 领域扩展 CRUD 资源定义。 */
public final class AigcDomainExtensionResource {

    public static final ResourceKey KEY = ResourceKey.of("aigc.domain-extension");
    public static final String BASE_PATH = "/api/aigc/domain-extensions";

    private static final CrudResourceTypeContract<AigcDomainExtension> TYPES =
            CrudResourceTypeContract.fromCrudController(
                    AigcDomainExtensionController.class, AigcDomainExtension.class);

    public static final CrudResourceDefinition<AigcDomainExtension> DEFINITION =
            CrudResourceDefinition.standard(
                    KEY,
                    TYPES,
                    new CrudResourceDescriptor("领域扩展", BASE_PATH, "aigc:domain-extension"),
                    CrudCapabilityDefinition.forTypes(TYPES)
                            .without(
                                    CrudOperation.DELETE_BATCH,
                                    CrudOperation.IMPORT,
                                    CrudOperation.RESTORE,
                                    CrudOperation.ARCHIVE),
                    new CrudQueryDefinition<>(
                            CrudFilterSchema.auto(),
                            Set.of(
                                    "code",
                                    "name",
                                    "extensionVersion",
                                    "industry",
                                    "region",
                                    "language",
                                    "status"),
                            Sort.by("id").descending()),
                    CrudMutationDefinition.forTypes(TYPES)
                            .withCustomUpdateCommands(
                                    java.util.Map.of("PUBLISH", Set.of("status"))),
                    CrudViewDefinition.forTypes(TYPES),
                    TenantScope.ORG_SHARED_WORKSPACE_OPTIONAL,
                    PersonalScope.none());

    private AigcDomainExtensionResource() {}
}
