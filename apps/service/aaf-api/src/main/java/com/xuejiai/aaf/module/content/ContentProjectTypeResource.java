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
import com.xuejiai.aaf.module.content.controller.ContentProjectTypeController;
import com.xuejiai.aaf.module.content.domain.ContentProjectType;

/**
 * 项目类型 CRUD 资源定义。
 *
 * @author AaronZZH & Kiro
 */
public final class ContentProjectTypeResource {

    public static final ResourceKey KEY = ResourceKey.of("content.project-type");
    public static final String BASE_PATH = "/api/content/project-types";

    private static final CrudResourceTypeContract<ContentProjectType> TYPES =
            CrudResourceTypeContract.fromCrudController(
                    ContentProjectTypeController.class, ContentProjectType.class);

    public static final CrudResourceDefinition<ContentProjectType> DEFINITION =
            CrudResourceDefinition.standard(
                    KEY,
                    TYPES,
                    new CrudResourceDescriptor("项目类型", BASE_PATH, "content:project-type"),
                    CrudCapabilityDefinition.forTypes(TYPES)
                            .without(
                                    CrudOperation.IMPORT,
                                    CrudOperation.RESTORE,
                                    CrudOperation.ARCHIVE,
                                    CrudOperation.CREATE,
                                    CrudOperation.UPDATE,
                                    CrudOperation.DELETE,
                                    CrudOperation.DELETE_BATCH),
                    new CrudQueryDefinition<>(
                            CrudFilterSchema.auto(),
                            Set.of(
                                    "code",
                                    "name",
                                    "icon",
                                    "description",
                                    "briefPlaceholder",
                                    "defaultChannels",
                                    "defaultProductionMode",
                                    "quickEntry",
                                    "builtin",
                                    "sortOrder",
                                    "status"),
                            Sort.by("id").descending()),
                    CrudMutationDefinition.forTypes(TYPES),
                    CrudViewDefinition.forTypes(TYPES)
                            .withFieldSet(
                                    "list",
                                    Set.of(
                                            "id",
                                            "code",
                                            "name",
                                            "icon",
                                            "description",
                                            "briefPlaceholder",
                                            "defaultChannels",
                                            "defaultProductionMode",
                                            "quickEntry",
                                            "builtin",
                                            "sortOrder",
                                            "status")),
                    TenantScope.ORG_SHARED_WORKSPACE_OPTIONAL,
                    PersonalScope.none());

    private ContentProjectTypeResource() {}
}
