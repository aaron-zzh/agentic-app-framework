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
import com.xuejiai.aaf.module.content.controller.ContentProjectObjectController;
import com.xuejiai.aaf.module.content.domain.ContentProjectObject;

/**
 * 项目对象 CRUD 资源定义。
 *
 * @author AaronZZH & Kiro
 */
public final class ContentProjectObjectResource {

    public static final ResourceKey KEY = ResourceKey.of("content.project-object");
    public static final String BASE_PATH = "/api/content/project-objects";

    private static final CrudResourceTypeContract<ContentProjectObject> TYPES =
            CrudResourceTypeContract.fromCrudController(
                    ContentProjectObjectController.class, ContentProjectObject.class);

    public static final CrudResourceDefinition<ContentProjectObject> DEFINITION =
            CrudResourceDefinition.standard(
                    KEY,
                    TYPES,
                    new CrudResourceDescriptor("项目对象", BASE_PATH, "content:project-object"),
                    CrudCapabilityDefinition.forTypes(TYPES)
                            .without(
                                    CrudOperation.IMPORT,
                                    CrudOperation.RESTORE,
                                    CrudOperation.ARCHIVE),
                    new CrudQueryDefinition<>(
                            CrudFilterSchema.empty(),
                            Set.of(
                                    "projectId",
                                    "objectType",
                                    "objectKey",
                                    "blueprintNodeKey",
                                    "parentId",
                                    "sortOrder",
                                    "title",
                                    "status",
                                    "source",
                                    "schemaVersion",
                                    "entityResource",
                                    "entityId",
                                    "adoptedVersionRef",
                                    "summary",
                                    "payload"),
                            Sort.by("id").descending()),
                    CrudMutationDefinition.forTypes(TYPES),
                    CrudViewDefinition.forTypes(TYPES)
                            .withFieldSet(
                                    "list",
                                    Set.of(
                                            "id",
                                            "version",
                                            "projectId",
                                            "objectType",
                                            "objectKey",
                                            "blueprintNodeKey",
                                            "parentId",
                                            "sortOrder",
                                            "title",
                                            "status",
                                            "source",
                                            "schemaVersion",
                                            "entityResource",
                                            "entityId",
                                            "adoptedVersionRef",
                                            "summary",
                                            "payload")),
                    TenantScope.ORG_SHARED_WORKSPACE_OPTIONAL,
                    PersonalScope.byProperty("ownerId"));

    private ContentProjectObjectResource() {}
}
