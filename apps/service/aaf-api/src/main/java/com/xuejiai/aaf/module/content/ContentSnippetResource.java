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
import com.xuejiai.aaf.module.content.controller.ContentSnippetController;
import com.xuejiai.aaf.module.content.domain.ContentSnippet;

/**
 * 创作片段 CRUD 资源定义。
 *
 * @author AaronZZH & Kiro
 */
public final class ContentSnippetResource {

    public static final ResourceKey KEY = ResourceKey.of("content.snippet");
    public static final String BASE_PATH = "/api/content/snippets";

    private static final CrudResourceTypeContract<ContentSnippet> TYPES =
            CrudResourceTypeContract.fromCrudController(
                    ContentSnippetController.class, ContentSnippet.class);

    public static final CrudResourceDefinition<ContentSnippet> DEFINITION =
            CrudResourceDefinition.standard(
                    KEY,
                    TYPES,
                    new CrudResourceDescriptor("创作片段", BASE_PATH, "content:snippet"),
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
                                    "referenceImageUrls",
                                    "variableSlots",
                                    "projectTypeCode",
                                    "brandProfileId",
                                    "useCount",
                                    "isPublic"),
                            Sort.by("id").descending()),
                    CrudMutationDefinition.forTypes(TYPES),
                    CrudViewDefinition.forTypes(TYPES)
                            .withFieldSet(
                                    "list",
                                    Set.of(
                                            "id",
                                            "name",
                                            "category",
                                            "content",
                                            "referenceImageUrls",
                                            "variableSlots",
                                            "projectTypeCode",
                                            "brandProfileId",
                                            "useCount",
                                            "isPublic")),
                    TenantScope.ORG_SHARED_WORKSPACE_OPTIONAL,
                    PersonalScope.byProperty("ownerId"));

    private ContentSnippetResource() {}
}
