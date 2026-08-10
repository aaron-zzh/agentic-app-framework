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
import com.xuejiai.aaf.module.ai.aigc.configuration.controller.AigcChannelSpecController;
import com.xuejiai.aaf.module.ai.aigc.configuration.domain.AigcChannelSpec;

/** AIGC 渠道规格 CRUD 资源定义。 */
public final class AigcChannelSpecResource {

    public static final ResourceKey KEY = ResourceKey.of("aigc.channel-spec");
    public static final String BASE_PATH = "/api/aigc/channel-specs";

    private static final CrudResourceTypeContract<AigcChannelSpec> TYPES =
            CrudResourceTypeContract.fromCrudController(
                    AigcChannelSpecController.class, AigcChannelSpec.class);

    public static final CrudResourceDefinition<AigcChannelSpec> DEFINITION =
            CrudResourceDefinition.standard(
                    KEY,
                    TYPES,
                    new CrudResourceDescriptor("渠道规格", BASE_PATH, "aigc:channel-spec"),
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
                                    "specVersion",
                                    "aspectRatio",
                                    "width",
                                    "height",
                                    "maxDurationSeconds",
                                    "exportFormat",
                                    "sortOrder",
                                    "status"),
                            Sort.by("sortOrder").ascending()),
                    CrudMutationDefinition.forTypes(TYPES)
                            .withCustomUpdateCommands(
                                    java.util.Map.of("PUBLISH", Set.of("status"))),
                    CrudViewDefinition.forTypes(TYPES),
                    TenantScope.GLOBAL,
                    PersonalScope.none());

    private AigcChannelSpecResource() {}
}
