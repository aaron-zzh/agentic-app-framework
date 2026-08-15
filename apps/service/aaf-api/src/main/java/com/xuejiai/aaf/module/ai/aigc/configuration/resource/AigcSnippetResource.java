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
import com.xuejiai.aaf.module.ai.aigc.configuration.controller.AigcSnippetController;
import com.xuejiai.aaf.module.ai.aigc.configuration.domain.AigcSnippet;

/** AIGC 创作片段 CRUD 资源定义。 */
public final class AigcSnippetResource {

    public static final ResourceKey KEY = ResourceKey.of("aigc.snippet");
    public static final String BASE_PATH = "/api/aigc/snippets";

    private static final CrudResourceTypeContract<AigcSnippet> TYPES =
            CrudResourceTypeContract.fromCrudController(
                    AigcSnippetController.class, AigcSnippet.class);

    public static final CrudResourceDefinition<AigcSnippet> DEFINITION =
            CrudResourceDefinition.standard(
                    KEY,
                    TYPES,
                    new CrudResourceDescriptor("创作片段", BASE_PATH, "aigc:snippet"),
                    CrudCapabilityDefinition.forTypes(TYPES)
                            .without(
                                    CrudOperation.IMPORT,
                                    CrudOperation.RESTORE,
                                    CrudOperation.ARCHIVE),
                    new CrudQueryDefinition<>(
                            CrudFilterSchema.auto(),
                            Set.of(
                                    "name",
                                    "category",
                                    "content",
                                    "referenceMediaVersionIds",
                                    "variableSlots",
                                    "projectTypeCode",
                                    "brandProfileId",
                                    "useCount",
                                    "isPublic"),
                            Sort.by("id").descending()),
                    CrudMutationDefinition.forTypes(TYPES),
                    CrudViewDefinition.forTypes(TYPES),
                    TenantScope.ORG_SHARED_WORKSPACE_OPTIONAL,
                    PersonalScope.none());

    private AigcSnippetResource() {}
}
