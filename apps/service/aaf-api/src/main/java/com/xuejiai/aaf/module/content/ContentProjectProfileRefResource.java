package com.xuejiai.aaf.module.content;

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
import com.xuejiai.aaf.module.content.controller.ContentProjectProfileRefController;
import com.xuejiai.aaf.module.content.domain.ContentProjectProfileRef;

/**
 * 项目资料引用 CRUD 资源定义。
 *
 * @author AaronZZH & Kiro
 */
public final class ContentProjectProfileRefResource {

    public static final ResourceKey KEY = ResourceKey.of("content.project-profile-ref");
    public static final String BASE_PATH = "/api/content/project-profile-refs";

    private static final CrudResourceTypeContract<ContentProjectProfileRef> TYPES =
            CrudResourceTypeContract.fromCrudController(
                    ContentProjectProfileRefController.class, ContentProjectProfileRef.class);

    public static final CrudResourceDefinition<ContentProjectProfileRef> DEFINITION =
            CrudResourceDefinition.standard(
                    KEY,
                    TYPES,
                    new CrudResourceDescriptor("项目资料引用", BASE_PATH, "content:project-profile-ref"),
                    CrudCapabilityDefinition.forTypes(TYPES)
                            .without(
                                    CrudOperation.IMPORT,
                                    CrudOperation.RESTORE,
                                    CrudOperation.ARCHIVE,
                                    CrudOperation.UPDATE),
                    new CrudQueryDefinition<>(
                            CrudFilterSchema.empty(),
                            Set.of(
                                    "projectId",
                                    "brandProfileId",
                                    "refScope",
                                    "profileVersion",
                                    "scopeNote"),
                            Sort.by("id").descending()),
                    CrudMutationDefinition.forTypes(TYPES),
                    CrudViewDefinition.forTypes(TYPES)
                            .withFieldSet(
                                    "list",
                                    Set.of(
                                            "id",
                                            "projectId",
                                            "brandProfileId",
                                            "refScope",
                                            "profileVersion",
                                            "scopeNote",
                                            "brandProfileName")),
                    TenantScope.ORG_SHARED_WORKSPACE_OPTIONAL,
                    PersonalScope.byProperty("ownerId"));

    private ContentProjectProfileRefResource() {}
}
