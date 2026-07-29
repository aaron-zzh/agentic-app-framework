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
import com.xuejiai.aaf.module.content.controller.ContentProjectRelationController;
import com.xuejiai.aaf.module.content.domain.ContentProjectRelation;

/**
 * 项目关系 CRUD 资源定义。
 *
 * @author AaronZZH & Kiro
 */
public final class ContentProjectRelationResource {

    public static final ResourceKey KEY = ResourceKey.of("content.project-relation");
    public static final String BASE_PATH = "/api/content/project-relations";

    private static final CrudResourceTypeContract<ContentProjectRelation> TYPES =
            CrudResourceTypeContract.fromCrudController(
                    ContentProjectRelationController.class, ContentProjectRelation.class);

    public static final CrudResourceDefinition<ContentProjectRelation> DEFINITION =
            CrudResourceDefinition.standard(
                    KEY,
                    TYPES,
                    new CrudResourceDescriptor("项目关系", BASE_PATH, "content:project-relation"),
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
                                    "relationType",
                                    "layer",
                                    "sourceObjectId",
                                    "targetObjectId",
                                    "relationMeta"),
                            Sort.by("id").descending()),
                    CrudMutationDefinition.forTypes(TYPES),
                    CrudViewDefinition.forTypes(TYPES)
                            .withFieldSet(
                                    "list",
                                    Set.of(
                                            "id",
                                            "projectId",
                                            "relationType",
                                            "layer",
                                            "sourceObjectId",
                                            "targetObjectId",
                                            "relationMeta")),
                    TenantScope.ORG_SHARED_WORKSPACE_OPTIONAL,
                    PersonalScope.byProperty("ownerId"));

    private ContentProjectRelationResource() {}
}
